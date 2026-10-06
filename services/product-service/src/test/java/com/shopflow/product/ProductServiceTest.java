package com.shopflow.product;

import com.shopflow.common.exception.ForbiddenException;
import com.shopflow.product.dto.ProductRequest;
import com.shopflow.security.AuthenticatedUser;
import com.shopflow.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests: which writes announce a change (and therefore evict the product cache). */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final AuthenticatedUser OWNER = new AuthenticatedUser(50L, "seller@test.com", Role.SELLER);
    private static final AuthenticatedUser OTHER_SELLER = new AuthenticatedUser(51L, "other@test.com", Role.SELLER);
    private static final ProductRequest NEW_DETAILS = new ProductRequest(
            "Keyboard Pro", "Mechanical", "electronics", new BigDecimal("59.99"), 5);

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProductService productService;

    @Test
    void updateByOwnerPublishesChangeEvent() {
        Product product = product();
        when(productRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(product));

        productService.update(1L, NEW_DETAILS, OWNER);

        assertThat(product.getPrice()).isEqualByComparingTo("59.99");
        verify(eventPublisher).publishEvent(ProductsChangedEvent.of(1L));
    }

    @Test
    void forbiddenUpdatePublishesNothing() {
        when(productRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(product()));

        assertThatThrownBy(() -> productService.update(1L, NEW_DETAILS, OTHER_SELLER))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deletePublishesChangeEvent() {
        Product product = product();
        when(productRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(product));

        productService.delete(1L, OWNER);

        assertThat(product.isActive()).isFalse();
        verify(eventPublisher).publishEvent(ProductsChangedEvent.of(1L));
    }

    private static Product product() {
        Product product = new Product(50L, "Keyboard", "Mechanical", "electronics", new BigDecimal("49.99"), 5);
        ReflectionTestUtils.setField(product, "id", 1L);
        return product;
    }
}
