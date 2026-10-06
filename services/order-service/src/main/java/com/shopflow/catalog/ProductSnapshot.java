package com.shopflow.catalog;

import java.math.BigDecimal;

/** Order-service's own copy of the product data it needs. Services share JSON, not classes. */
public record ProductSnapshot(Long id, String name, BigDecimal price, int stockQuantity, boolean active) {
}
