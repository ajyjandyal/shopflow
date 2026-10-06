package com.shopflow.cart.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shopflow.cart.CartItem;
import com.shopflow.catalog.ProductSnapshot;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CartItemResponse(
        Long productId,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        boolean available,
        String message
) {
    public static CartItemResponse of(CartItem item, ProductSnapshot product) {
        if (product == null || !product.active()) {
            return new CartItemResponse(item.getProductId(), null, null, item.getQuantity(),
                    BigDecimal.ZERO, false, "This product is no longer available");
        }
        BigDecimal lineTotal = product.price().multiply(BigDecimal.valueOf(item.getQuantity()));
        boolean inStock = product.stockQuantity() >= item.getQuantity();
        String message = inStock ? null : "Only " + product.stockQuantity() + " units in stock";
        return new CartItemResponse(product.id(), product.name(), product.price(),
                item.getQuantity(), lineTotal, inStock, message);
    }
}
