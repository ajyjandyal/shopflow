package com.shopflow.order.dto;

import com.shopflow.order.Order;
import com.shopflow.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(Long id, Long userId, OrderStatus status, BigDecimal totalAmount,
                            List<OrderItemResponse> items, Instant createdAt, Instant updatedAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getUserId(), order.getStatus(), order.getTotalAmount(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getCreatedAt(), order.getUpdatedAt());
    }
}
