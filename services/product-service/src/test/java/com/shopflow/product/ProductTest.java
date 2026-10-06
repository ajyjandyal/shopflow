package com.shopflow.product;

import com.shopflow.common.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    @Test
    void decreaseStockReducesQuantity() {
        Product product = newProduct(10);
        product.decreaseStock(4);
        assertThat(product.getStockQuantity()).isEqualTo(6);
    }

    @Test
    void stockCanNeverGoNegative() {
        Product product = newProduct(2);
        assertThatThrownBy(() -> product.decreaseStock(3)).isInstanceOf(ConflictException.class);
        assertThat(product.getStockQuantity()).isEqualTo(2);
    }

    @Test
    void nonPositiveQuantitiesAreRejected() {
        Product product = newProduct(2);
        assertThatThrownBy(() -> product.decreaseStock(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> product.increaseStock(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void likeWildcardsAreEscaped() {
        assertThat(ProductSpecifications.escapeLike("50%_off\\")).isEqualTo("50\\%\\_off\\\\");
    }

    private static Product newProduct(int stock) {
        return new Product(1L, "Mouse", "Wireless", "electronics", new BigDecimal("19.99"), stock);
    }
}
