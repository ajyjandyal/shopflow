package com.shopflow.order;

import com.shopflow.cart.Cart;
import com.shopflow.cart.CartItem;
import com.shopflow.cart.CartRepository;
import com.shopflow.catalog.ProductClient;
import com.shopflow.catalog.ProductServiceUnavailableException;
import com.shopflow.catalog.ReservationResult;
import com.shopflow.common.exception.BadRequestException;
import com.shopflow.common.exception.ConflictException;
import com.shopflow.common.exception.ResourceNotFoundException;
import com.shopflow.common.web.PageResponse;
import com.shopflow.order.dto.OrderResponse;
import com.shopflow.order.dto.OrderSummaryResponse;
import com.shopflow.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * In Phase 1 checkout was ONE database transaction. Now stock lives in product-service's
 * database and orders live here, so no single transaction can cover both. Instead:
 *
 *   1. Reserve stock remotely (idempotent, keyed by a reservationId we generate).
 *   2. Save the order locally in a short transaction.
 *   3. If step 2 fails, COMPENSATE by releasing the reservation.
 *
 * This "do, then undo on failure" approach is the basic idea behind the Saga pattern.
 * Its weak spot: if the compensation itself fails, stock stays reserved without an order.
 * We log that loudly; Phase 4 (Kafka + outbox) removes this gap.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    static final int MAX_RESERVE_ATTEMPTS = 3;
    static final long RETRY_BACKOFF_MILLIS = 200;

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductClient productClient;
    private final TransactionTemplate transactionTemplate;

    public OrderService(OrderRepository orderRepository, CartRepository cartRepository,
                        ProductClient productClient, TransactionTemplate transactionTemplate) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.productClient = productClient;
        this.transactionTemplate = transactionTemplate;
    }

    /** Deliberately NOT @Transactional: it spans a network call (see CartService). */
    public OrderResponse placeOrder(Long userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .filter(c -> !c.isEmpty())
                .orElseThrow(() -> new BadRequestException("Your cart is empty"));

        Map<Long, Integer> quantities = cart.getItems().stream()
                .collect(Collectors.toMap(CartItem::getProductId, CartItem::getQuantity, Integer::sum, TreeMap::new));

        UUID reservationId = UUID.randomUUID();
        ReservationResult reservation = reserveWithRetry(reservationId, quantities);

        try {
            return transactionTemplate.execute(status -> saveConfirmedOrder(userId, reservationId, reservation));
        } catch (RuntimeException ex) {
            log.error("Saving order failed after stock was reserved (reservation {}). Compensating.", reservationId, ex);
            releaseQuietly(reservationId);
            throw ex;
        }
    }

    /**
     * Retrying a WRITE is only safe because reserve() is idempotent: if the first attempt
     * actually succeeded but the response was lost, the retry gets the same reservation
     * back instead of taking stock twice.
     */
    private ReservationResult reserveWithRetry(UUID reservationId, Map<Long, Integer> quantities) {
        for (int attempt = 1; ; attempt++) {
            try {
                return productClient.reserve(reservationId, quantities);
            } catch (ProductServiceUnavailableException ex) {
                if (attempt >= MAX_RESERVE_ATTEMPTS) {
                    // Outcome unknown: the reservation MAY exist. Release it to be safe.
                    // Safe even if it never existed (product-service stores a tombstone).
                    log.warn("Reserve failed after {} attempts (reservation {}); releasing", attempt, reservationId);
                    releaseQuietly(reservationId);
                    throw ex;
                }
                log.warn("Reserve attempt {} failed (reservation {}); retrying", attempt, reservationId);
                sleepBeforeRetry(attempt);
            }
            // ConflictException (e.g. out of stock) is a definite "no": never retried.
        }
    }

    private OrderResponse saveConfirmedOrder(Long userId, UUID reservationId, ReservationResult reservation) {
        Order order = new Order(userId, reservationId);
        for (ReservationResult.Line line : reservation.lines()) {
            order.addItem(new OrderItem(line.productId(), line.productName(), line.unitPrice(), line.quantity()));
        }
        order.changeStatus(OrderStatus.CONFIRMED);
        Order saved = orderRepository.save(order);
        cartRepository.findByUserId(userId).ifPresent(Cart::clear);

        log.info("Order id={} placed by user id={} items={} total={} reservation={}",
                saved.getId(), userId, saved.getItems().size(), saved.getTotalAmount(), reservationId);
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
            log.info("Order id={} status {} -> {} by user id={}", orderId, previous, target, adminId);
            return OrderResponse.from(managed);
        });
    }

    /**
     * Release stock FIRST, then mark the order cancelled. If the local update then fails,
     * the user can simply retry: release is idempotent, so the second call is a no-op.
     * The opposite order (cancel first) could leave a cancelled order whose stock is never returned.
     */
    private OrderResponse cancelOrder(Order order, Long actorId) {
        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            throw new ConflictException("Cannot change order status from " + order.getStatus() + " to CANCELLED");
        }
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            productClient.release(order.getReservationId());
        }
        return transactionTemplate.execute(status -> {
            Order managed = reload(order.getId());
            OrderStatus previous = managed.getStatus();
            managed.changeStatus(OrderStatus.CANCELLED);
            log.info("Order id={} status {} -> CANCELLED by user id={}", managed.getId(), previous, actorId);
            return OrderResponse.from(managed);
        });
    }

    private Order reload(Long orderId) {
        return orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
    }

    private Order loadAccessibleOrder(Long orderId, AuthenticatedUser user) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
        if (!user.isAdmin() && !order.belongsTo(user.id())) {
            throw ResourceNotFoundException.of("Order", orderId);
        }
        return order;
    }

    private void releaseQuietly(UUID reservationId) {
        try {
            productClient.release(reservationId);
        } catch (RuntimeException ex) {
            // The one gap this design cannot close on its own. Phase 4 fixes it.
            log.error("COMPENSATION FAILED: reservation {} may still hold stock. Manual release needed.",
                    reservationId, ex);
        }
    }

    private static void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_BACKOFF_MILLIS * attempt); // linear backoff: 200ms, 400ms
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying", interrupted);
        }
    }
}
