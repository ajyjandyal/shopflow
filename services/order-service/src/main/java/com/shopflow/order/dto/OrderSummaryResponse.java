package com.shopflow.order.dto;

import com.shopflow.order.Order;
import com.shopflow.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** Used in paginated lists. Contains no items, so listing orders never triggers item queries. */
public record OrderSummaryResponse(Long id, Long userId, OrderStatus status, BigDecimal totalAmount,
                                   Instant createdAt, Instant updatedAt) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(order.getId(), order.getUserId(), order.getStatus(),
                order.getTotalAmount(), order.getCreatedAt(), order.getUpdatedAt());
    }
}
