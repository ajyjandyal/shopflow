package com.shopflow.inventory.dto;

import com.shopflow.product.Product;

import java.math.BigDecimal;

/** What other services need to know about a product. Includes inactive products, flagged. */
public record ProductSnapshot(Long id, String name, BigDecimal price, int stockQuantity, boolean active) {

    public static ProductSnapshot from(Product product) {
        return new ProductSnapshot(product.getId(), product.getName(), product.getPrice(),
                product.getStockQuantity(), product.isActive());
    }
}
