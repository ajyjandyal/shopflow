package com.shopflow.order;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Order lifecycle as a finite state machine:
 *
 *   PENDING --> CONFIRMED --> SHIPPED --> DELIVERED
 *      |            |
 *      +------------+--> CANCELLED
 *
 * In Phase 1 an order becomes CONFIRMED immediately because stock is reserved in the same
 * transaction. In Phase 4 (Kafka) orders will wait in PENDING until the Inventory Service
 * confirms the reservation asynchronously, which is why PENDING already exists.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(PENDING, EnumSet.of(CONFIRMED, CANCELLED));
        ALLOWED_TRANSITIONS.put(CONFIRMED, EnumSet.of(SHIPPED, CANCELLED));
        ALLOWED_TRANSITIONS.put(SHIPPED, EnumSet.of(DELIVERED));
        ALLOWED_TRANSITIONS.put(DELIVERED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED_TRANSITIONS.get(this).isEmpty();
    }
}
