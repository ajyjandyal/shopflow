package com.shopflow.common.event;

import java.time.Instant;
import java.util.UUID;

public record OrderCancelledEvent(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        Long orderId,
        UUID reservationId
) {
}
