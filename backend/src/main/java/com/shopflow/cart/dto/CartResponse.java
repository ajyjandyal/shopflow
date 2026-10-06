package com.shopflow.cart.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        List<CartItemResponse> items,
        int totalQuantity,
        BigDecimal subtotal,
        boolean checkoutReady
) {
    public static CartResponse of(List<CartItemResponse> items) {
        int totalQuantity = items.stream()
                .filter(CartItemResponse::available)
                .mapToInt(CartItemResponse::quantity)
                .sum();
        BigDecimal subtotal = items.stream()
                .filter(CartItemResponse::available)
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean checkoutReady = !items.isEmpty() && items.stream().allMatch(CartItemResponse::available);
        return new CartResponse(items, totalQuantity, subtotal, checkoutReady);
    }

    public static CartResponse empty() {
        return of(List.of());
    }
}
