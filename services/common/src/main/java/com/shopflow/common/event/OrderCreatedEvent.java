package com.shopflow.common.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        Long orderId,
        Long userId,
        UUID reservationId,
        List<OrderCreatedItem> items
) {
}
