package com.shopflow.product.dto;

import com.shopflow.product.Product;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        Long sellerId,
        String name,
        String description,
        String category,
        BigDecimal price,
        int stockQuantity,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getSellerId(), product.getName(),
                product.getDescription(), product.getCategory(), product.getPrice(),
                product.getStockQuantity(), product.getCreatedAt(), product.getUpdatedAt());
    }
}
