package com.shopflow.order.dto;

import com.shopflow.order.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(Long productId, String productName, BigDecimal unitPrice,
                                int quantity, BigDecimal lineTotal) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getProductId(), item.getProductName(), item.getUnitPrice(),
                item.getQuantity(), item.getLineTotal());
    }
}
