package com.shopflow.product;

import java.util.Set;

/**
 * Published (inside the database transaction) whenever product data that appears in the
 * public product response changes: details, price, active flag or stock.
 *
 * It is a plain record, not a Spring ApplicationEvent subclass: since Spring 4.2 any
 * object can be published as an event.
 */
public record ProductsChangedEvent(Set<Long> productIds) {

    public ProductsChangedEvent {
        productIds = Set.copyOf(productIds); // defensive, immutable copy
    }

    public static ProductsChangedEvent of(Long productId) {
        return new ProductsChangedEvent(Set.of(productId));
    }
}
