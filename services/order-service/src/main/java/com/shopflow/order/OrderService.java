package com.shopflow.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.cart.Cart;
import com.shopflow.cart.CartItem;
import com.shopflow.cart.CartRepository;
import com.shopflow.catalog.ProductClient;
import com.shopflow.catalog.ProductSnapshot;
import com.shopflow.common.event.OrderCancelledEvent;
import com.shopflow.common.event.OrderCreatedEvent;
import com.shopflow.common.event.OrderCreatedItem;
import com.shopflow.common.exception.BadRequestException;
import com.shopflow.common.exception.ConflictException;
import com.shopflow.common.exception.ResourceNotFoundException;
import com.shopflow.common.web.PageResponse;
import com.shopflow.order.dto.OrderResponse;
import com.shopflow.order.dto.OrderSummaryResponse;
import com.shopflow.outbox.OutboxRepository;
import com.shopflow.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final String ORDER_CREATED_TOPIC = "order.created.v1";
    private static final String ORDER_CANCELLED_TOPIC = "order.cancelled.v1";

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductClient productClient;
    private final TransactionTemplate transactionTemplate;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OrderService(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            ProductClient productClient,
            TransactionTemplate transactionTemplate,
            OutboxRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.productClient = productClient;
        this.transactionTemplate = transactionTemplate;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public OrderResponse placeOrder(Long userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .filter(c -> !c.isEmpty())
                .orElseThrow(() -> new BadRequestException("Your cart is empty"));

        Map<Long, Integer> quantities = cart.getItems().stream()
                .collect(Collectors.toMap(
                        CartItem::getProductId,
                        CartItem::getQuantity,
                        Integer::sum,
                        TreeMap::new
                ));

        List<ProductSnapshot> snapshots = productClient.getSnapshots(quantities.keySet());

        Map<Long, ProductSnapshot> products = snapshots.stream()
                .collect(Collectors.toMap(ProductSnapshot::id, Function.identity()));

        for (Long productId : quantities.keySet()) {
            ProductSnapshot product = products.get(productId);

            if (product == null || !product.active()) {
                throw new ConflictException(
                        "Product " + productId + " is no longer available"
                );
            }
        }

        UUID reservationId = UUID.randomUUID();

        return transactionTemplate.execute(status -> {
            Order order = new Order(userId, reservationId);

            for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
                ProductSnapshot product = products.get(entry.getKey());

                order.addItem(new OrderItem(
                        product.id(),
                        product.name(),
                        product.price(),
                        entry.getValue()
                ));
            }

            Order saved = orderRepository.save(order);

            cartRepository.findByUserId(userId).ifPresent(Cart::clear);

            OrderCreatedEvent event = new OrderCreatedEvent(
                    UUID.randomUUID(),
                    1,
                    Instant.now(),
                    saved.getId(),
                    userId,
                    reservationId,
                    quantities.entrySet().stream()
                            .map(entry -> new OrderCreatedItem(
                                    entry.getKey(),
                                    entry.getValue()
                            ))
                            .toList()
            );

            outboxRepository.insert(
                    event.eventId(),
                    "order-created:" + saved.getId(),
                    ORDER_CREATED_TOPIC,
                    String.valueOf(saved.getId()),
                    serialize(event)
            );

            log.info(
                    "Order id={} created PENDING for user id={} reservation={}",
                    saved.getId(),
                    userId,
                    reservationId
            );

            return OrderResponse.from(saved);
        });
    }

    public OrderResponse cancel(Long orderId, AuthenticatedUser user) {
        Order order = loadAccessibleOrder(orderId, user);
        return cancelOrder(order, user.id());
    }

    public OrderResponse updateStatus(Long orderId, OrderStatus target, Long adminId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));

        if (target == OrderStatus.CANCELLED) {
            return cancelOrder(order, adminId);
        }

        return transactionTemplate.execute(status -> {
            Order managed = reload(orderId);
            OrderStatus previous = managed.getStatus();

            managed.changeStatus(target);

            log.info(
                    "Order id={} status {} -> {} by user id={}",
                    orderId,
                    previous,
                    target,
                    adminId
            );

            return OrderResponse.from(managed);
        });
    }

    private OrderResponse cancelOrder(Order order, Long actorId) {
        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            throw new ConflictException(
                    "Cannot change order status from "
                            + order.getStatus()
                            + " to CANCELLED"
            );
        }

        return transactionTemplate.execute(status -> {
            Order managed = reload(order.getId());

            if (managed.getStatus() == OrderStatus.CANCELLED) {
                return OrderResponse.from(managed);
            }

            managed.changeStatus(OrderStatus.CANCELLED);

            OrderCancelledEvent event = new OrderCancelledEvent(
                    UUID.randomUUID(),
                    1,
                    Instant.now(),
                    managed.getId(),
                    managed.getReservationId()
            );

            outboxRepository.insert(
                    event.eventId(),
                    "order-cancelled:" + managed.getId(),
                    ORDER_CANCELLED_TOPIC,
                    String.valueOf(managed.getId()),
                    serialize(event)
            );

            log.info(
                    "Order id={} status -> CANCELLED by user id={}",
                    managed.getId(),
                    actorId
            );

            return OrderResponse.from(managed);
        });
    }

    public PageResponse<OrderSummaryResponse> listMyOrders(
            Long userId,
            Pageable pageable
    ) {
        return PageResponse.from(
                orderRepository.findByUserId(userId, pageable),
                OrderSummaryResponse::from
        );
    }

    public PageResponse<OrderSummaryResponse> listAll(
            OrderStatus status,
            Pageable pageable
    ) {
        Page<Order> page = status == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatus(status, pageable);

        return PageResponse.from(page, OrderSummaryResponse::from);
    }

    public OrderResponse getOrder(
            Long orderId,
            AuthenticatedUser user
    ) {
        return OrderResponse.from(loadAccessibleOrder(orderId, user));
    }

    private Order reload(Long orderId) {
        return orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
    }

    private Order loadAccessibleOrder(
            Long orderId,
            AuthenticatedUser user
    ) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));

        if (!user.isAdmin() && !order.belongsTo(user.id())) {
            throw ResourceNotFoundException.of("Order", orderId);
        }

        return order;
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Could not serialize Kafka event",
                    ex
            );
        }
    }
}
