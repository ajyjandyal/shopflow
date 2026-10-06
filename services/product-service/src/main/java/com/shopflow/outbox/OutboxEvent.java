package com.shopflow.outbox;

import java.time.Instant;
import java.util.UUID;

public record OutboxEvent(
        UUID id,
        String eventKey,
        String topic,
        String aggregateId,
        String payload,
        Instant createdAt,
        int attempts
) {
}
