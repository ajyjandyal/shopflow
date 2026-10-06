package com.shopflow.order;

import com.shopflow.cart.Cart;
import com.shopflow.cart.CartRepository;
import com.shopflow.catalog.ProductClient;
import com.shopflow.catalog.ProductServiceUnavailableException;
import com.shopflow.catalog.ReservationResult;
import com.shopflow.common.exception.BadRequestException;
import com.shopflow.common.exception.ConflictException;
import com.shopflow.common.exception.ResourceNotFoundException;
import com.shopflow.order.dto.OrderResponse;
import com.shopflow.security.AuthenticatedUser;
import com.shopflow.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 100L;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CartRepository cartRepository;
    @Mock
    private ProductClient productClient;
    @Mock
    private TransactionTemplate transactionTemplate;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        // Run "transactional" lambdas directly: unit tests have no real database.
        lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        orderService = new OrderService(orderRepository, cartRepository, productClient, transactionTemplate);
    }

    @Test
    void emptyCartIsRejectedWithoutCallingProductService() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(new Cart(USER_ID)));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(productClient);
    }

    @Test
    void successfulCheckoutCreatesConfirmedOrderAndClearsCart() {
        Cart cart = cartWith(10L, 2);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(productClient.reserve(any(UUID.class), anyMap())).thenReturn(reservation(10L, 2));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.placeOrder(USER_ID);

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.totalAmount()).isEqualByComparingTo("99.98");
        assertThat(cart.isEmpty()).isTrue();
        verify(productClient, never()).release(any());
    }

    @Test
    void outOfStockIsDefiniteFailureNoRetryNoCompensation() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cartWith(10L, 2)));
        when(productClient.reserve(any(UUID.class), anyMap())).thenThrow(new ConflictException("Insufficient stock"));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID)).isInstanceOf(ConflictException.class);
        verify(productClient, times(1)).reserve(any(UUID.class), anyMap());
        verify(productClient, never()).release(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void transientFailureIsRetriedWithTheSameReservationId() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cartWith(10L, 2)));
        when(productClient.reserve(any(UUID.class), anyMap()))
                .thenThrow(new ProductServiceUnavailableException(new RuntimeException("timeout")))
                .thenReturn(reservation(10L, 2));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.placeOrder(USER_ID);

        ArgumentCaptor<UUID> ids = ArgumentCaptor.forClass(UUID.class);
        verify(productClient, times(2)).reserve(ids.capture(), anyMap());
        assertThat(ids.getAllValues().get(0)).isEqualTo(ids.getAllValues().get(1));
        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void productServiceDownRetriesThenReleasesJustInCase() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cartWith(10L, 2)));
        when(productClient.reserve(any(UUID.class), anyMap()))
                .thenThrow(new ProductServiceUnavailableException(new RuntimeException("connection refused")));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID))
                .isInstanceOf(ProductServiceUnavailableException.class);

        ArgumentCaptor<UUID> reservedId = ArgumentCaptor.forClass(UUID.class);
        verify(productClient, times(OrderService.MAX_RESERVE_ATTEMPTS)).reserve(reservedId.capture(), anyMap());
        verify(productClient).release(reservedId.getValue());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void localSaveFailureCompensatesByReleasingStock() {
        Cart cart = cartWith(10L, 2);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(productClient.reserve(any(UUID.class), anyMap())).thenReturn(reservation(10L, 2));
        when(orderRepository.save(any(Order.class))).thenThrow(new RuntimeException("database unavailable"));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID)).isInstanceOf(RuntimeException.class);

        ArgumentCaptor<UUID> reservedId = ArgumentCaptor.forClass(UUID.class);
        verify(productClient).reserve(reservedId.capture(), anyMap());
        verify(productClient).release(reservedId.getValue());
        assertThat(cart.isEmpty()).isFalse();
    }

    @Test
    void cancellingConfirmedOrderReleasesStockBeforeUpdatingStatus() {
        Order order = confirmedOrder(USER_ID);
        when(orderRepository.findWithItemsById(ORDER_ID)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.cancel(ORDER_ID, user(USER_ID));

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        InOrder sequence = inOrder(productClient, orderRepository);
        sequence.verify(productClient).release(order.getReservationId());
        sequence.verify(orderRepository).findWithItemsById(ORDER_ID);
    }

    @Test
    void ifReleaseFailsTheOrderStaysConfirmedSoTheUserCanRetry() {
        Order order = confirmedOrder(USER_ID);
        when(orderRepository.findWithItemsById(ORDER_ID)).thenReturn(Optional.of(order));
        org.mockito.Mockito.doThrow(new ProductServiceUnavailableException(new RuntimeException("down")))
                .when(productClient).release(order.getReservationId());

        assertThatThrownBy(() -> orderService.cancel(ORDER_ID, user(USER_ID)))
                .isInstanceOf(ProductServiceUnavailableException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void userCannotSeeAnotherUsersOrder() {
        when(orderRepository.findWithItemsById(ORDER_ID)).thenReturn(Optional.of(confirmedOrder(USER_ID)));

        assertThatThrownBy(() -> orderService.getOrder(ORDER_ID, user(999L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static Cart cartWith(Long productId, int quantity) {
        Cart cart = new Cart(USER_ID);
        cart.setItemQuantity(productId, quantity);
        return cart;
    }

    private static ReservationResult reservation(Long productId, int quantity) {
        return new ReservationResult(UUID.randomUUID(), "RESERVED",
                List.of(new ReservationResult.Line(productId, "Keyboard", new BigDecimal("49.99"), quantity)));
    }

    private static Order confirmedOrder(Long userId) {
        Order order = new Order(userId, UUID.randomUUID());
        order.addItem(new OrderItem(10L, "Keyboard", new BigDecimal("49.99"), 2));
        order.changeStatus(OrderStatus.CONFIRMED);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        return order;
    }

    private static AuthenticatedUser user(Long id) {
        return new AuthenticatedUser(id, "user" + id + "@test.com", Role.USER);
    }
}
