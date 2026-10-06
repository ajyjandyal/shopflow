package com.shopflow.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.security.AuthenticatedUser;
import com.shopflow.security.Role;
import com.shopflow.cart.Cart;
import com.shopflow.cart.CartRepository;
import com.shopflow.catalog.ProductClient;
import com.shopflow.common.exception.BadRequestException;
import com.shopflow.common.exception.ResourceNotFoundException;
import com.shopflow.outbox.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                cartRepository,
                productClient,
                transactionTemplate,
                outboxRepository,
                objectMapper
        );
    }

    @Test
    void emptyCartIsRejected() {
        when(cartRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(new Cart(USER_ID)));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(productClient);
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void missingCartIsRejected() {
        when(cartRepository.findByUserId(USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(productClient);
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void userCannotSeeAnotherUsersOrder() {
        Order order = new Order(
                999L,
                UUID.randomUUID()
        );

        when(orderRepository.findWithItemsById(ORDER_ID))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrder(ORDER_ID, new AuthenticatedUser(USER_ID, "test@example.com", Role.USER)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
