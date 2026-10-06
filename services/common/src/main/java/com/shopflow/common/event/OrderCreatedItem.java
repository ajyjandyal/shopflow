package com.shopflow.common.event;

public record OrderCreatedItem(
        Long productId,
        int quantity
) {
}
