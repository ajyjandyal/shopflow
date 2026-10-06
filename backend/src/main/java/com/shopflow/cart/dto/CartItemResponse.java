package com.shopflow.cart.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shopflow.cart.CartItem;
import com.shopflow.product.Product;

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
    /**
     * Product may be null (deleted) or inactive, or stock may have dropped since the item
     * was added. We still show the item but flag it, instead of silently dropping it.
     */
    public static CartItemResponse of(CartItem item, Product product) {
        if (product == null || !product.isActive()) {
            return new CartItemResponse(item.getProductId(), null, null, item.getQuantity(),
                    BigDecimal.ZERO, false, "This product is no longer available");
        }
        BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        boolean inStock = product.getStockQuantity() >= item.getQuantity();
        String message = inStock ? null : "Only " + product.getStockQuantity() + " units in stock";
        return new CartItemResponse(product.getId(), product.getName(), product.getPrice(),
                item.getQuantity(), lineTotal, inStock, message);
    }
}
