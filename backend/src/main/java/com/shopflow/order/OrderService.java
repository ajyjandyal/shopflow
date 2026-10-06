package com.shopflow.order;

import com.shopflow.cart.Cart;
import com.shopflow.cart.CartItem;
import com.shopflow.cart.CartRepository;
import com.shopflow.common.exception.BadRequestException;
import com.shopflow.common.exception.ConflictException;
import com.shopflow.common.exception.ResourceNotFoundException;
import com.shopflow.common.web.PageResponse;
import com.shopflow.order.dto.OrderResponse;
import com.shopflow.order.dto.OrderSummaryResponse;
import com.shopflow.product.Product;
import com.shopflow.product.ProductRepository;
import com.shopflow.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository, CartRepository cartRepository,
                        ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
    }

    /**
     * Checkout: converts the cart into an order inside ONE database transaction.
     *
     * 1. Lock every product row in the cart (SELECT ... FOR UPDATE, in id order).
     * 2. Check stock and decrement it.        } if ANY step throws, the whole
     * 3. Create the order with price snapshots. } transaction rolls back: no stock is
     * 4. Empty the cart.                        } lost and no half-created order exists.
     *
     * The row locks make concurrent checkouts of the same product wait for each other, so
     * stock can never be oversold. This is ACID atomicity + isolation doing the work.
     */
    @Transactional
    public OrderResponse placeOrder(Long userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .filter(c -> !c.isEmpty())
                .orElseThrow(() -> new BadRequestException("Your cart is empty"));

        Map<Long, Integer> requestedQuantities = cart.getItems().stream()
                .collect(Collectors.toMap(CartItem::getProductId, CartItem::getQuantity));

        Map<Long, Product> lockedProducts = productRepository.findAllByIdForUpdate(requestedQuantities.keySet())
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Order order = new Order(userId);
        for (CartItem item : cart.getItems()) {
            Product product = lockedProducts.get(item.getProductId());
            if (product == null || !product.isActive()) {
                throw new ConflictException("Product " + item.getProductId()
                        + " is no longer available. Remove it from your cart and try again.");
            }
            product.decreaseStock(item.getQuantity());
            order.addItem(new OrderItem(product.getId(), product.getName(), product.getPrice(), item.getQuantity()));
        }
        order.changeStatus(OrderStatus.CONFIRMED);

        Order saved = orderRepository.save(order);
        cart.clear();

        log.info("Order id={} placed by user id={} items={} total={}",
                saved.getId(), userId, saved.getItems().size(), saved.getTotalAmount());
        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listMyOrders(Long userId, Pageable pageable) {
        return PageResponse.from(orderRepository.findByUserId(userId, pageable), OrderSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listAll(OrderStatus status, Pageable pageable) {
        Page<Order> page = status == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatus(status, pageable);
        return PageResponse.from(page, OrderSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId, AuthenticatedUser user) {
        return OrderResponse.from(loadAccessibleOrder(orderId, user));
    }

    @Transactional
    public OrderResponse cancel(Long orderId, AuthenticatedUser user) {
        Order order = loadAccessibleOrder(orderId, user);
        transition(order, OrderStatus.CANCELLED, user.id());
        return OrderResponse.from(order);
    }

    /** Admin-only operation (enforced by URL rule + @PreAuthorize on the controller). */
    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus target, Long adminId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
        transition(order, target, adminId);
        return OrderResponse.from(order);
    }

    private void transition(Order order, OrderStatus target, Long actorId) {
        OrderStatus previous = order.getStatus();
        order.changeStatus(target);
        if (target == OrderStatus.CANCELLED && previous == OrderStatus.CONFIRMED) {
            releaseStock(order);
        }
        log.info("Order id={} status {} -> {} by user id={}", order.getId(), previous, target, actorId);
    }

    /** Cancelled CONFIRMED orders give their reserved stock back, with the same locking rules. */
    private void releaseStock(Order order) {
        Map<Long, Integer> quantities = order.getItems().stream()
                .collect(Collectors.groupingBy(OrderItem::getProductId,
                        Collectors.summingInt(OrderItem::getQuantity)));
        for (Product product : productRepository.findAllByIdForUpdate(quantities.keySet())) {
            product.increaseStock(quantities.get(product.getId()));
        }
    }

    /**
     * Returns 404 (not 403) when a user requests someone else's order. A 403 would confirm
     * that order id exists, leaking information (an IDOR-hardening practice).
     */
    private Order loadAccessibleOrder(Long orderId, AuthenticatedUser user) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
        if (!user.isAdmin() && !order.belongsTo(user.id())) {
            throw ResourceNotFoundException.of("Order", orderId);
        }
        return order;
    }
}
