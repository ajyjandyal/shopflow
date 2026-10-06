package com.shopflow.order;

import com.shopflow.cart.Cart;
import com.shopflow.cart.CartRepository;
import com.shopflow.common.exception.BadRequestException;
import com.shopflow.common.exception.ConflictException;
import com.shopflow.common.exception.ResourceNotFoundException;
import com.shopflow.order.dto.OrderResponse;
import com.shopflow.product.Product;
import com.shopflow.product.ProductRepository;
import com.shopflow.security.AuthenticatedUser;
import com.shopflow.user.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CartRepository cartRepository;
    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void placeOrderFailsWhenUserHasNoCart() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(orderRepository, productRepository);
    }

    @Test
    void placeOrderFailsWhenCartIsEmpty() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(new Cart(USER_ID)));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void placeOrderReservesStockSnapshotsPricesAndClearsCart() {
        Product keyboard = product(10L, "Keyboard", "49.99", 5);
        Cart cart = new Cart(USER_ID);
        cart.setItemQuantity(10L, 2);

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(productRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(keyboard));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.placeOrder(USER_ID);

        assertThat(keyboard.getStockQuantity()).isEqualTo(3);
        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.totalAmount()).isEqualByComparingTo("99.98");
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.productName()).isEqualTo("Keyboard");
            assertThat(item.unitPrice()).isEqualByComparingTo("49.99");
            assertThat(item.quantity()).isEqualTo(2);
        });
        assertThat(cart.isEmpty()).isTrue();
    }

    @Test
    void placeOrderFailsAndSavesNothingWhenStockIsInsufficient() {
        Product keyboard = product(10L, "Keyboard", "49.99", 1);
        Cart cart = new Cart(USER_ID);
        cart.setItemQuantity(10L, 2);

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(productRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(keyboard));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID)).isInstanceOf(ConflictException.class);
        verify(orderRepository, never()).save(any());
        assertThat(cart.isEmpty()).isFalse();
    }

    @Test
    void cancellingConfirmedOrderRestoresStock() {
        Product keyboard = product(10L, "Keyboard", "49.99", 3);
        Order order = confirmedOrder(USER_ID, keyboard, 2);

        when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));
        when(productRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(keyboard));

        OrderResponse response = orderService.cancel(100L, new AuthenticatedUser(USER_ID, "u@x.com", Role.USER));

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(keyboard.getStockQuantity()).isEqualTo(5);
    }

    @Test
    void userCannotSeeAnotherUsersOrder() {
        Order order = confirmedOrder(USER_ID, product(10L, "Keyboard", "49.99", 3), 1);
        when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));

        AuthenticatedUser stranger = new AuthenticatedUser(999L, "other@x.com", Role.USER);

        assertThatThrownBy(() -> orderService.getOrder(100L, stranger))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static Product product(Long id, String name, String price, int stock) {
        Product product = new Product(50L, name, null, "electronics", new BigDecimal(price), stock);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private static Order confirmedOrder(Long userId, Product product, int quantity) {
        Order order = new Order(userId);
        order.addItem(new OrderItem(product.getId(), product.getName(), product.getPrice(), quantity));
        order.changeStatus(OrderStatus.CONFIRMED);
        ReflectionTestUtils.setField(order, "id", 100L);
        return order;
    }
}
