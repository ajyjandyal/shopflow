package com.shopflow.product;

/**
 * Names shared by everything that touches the product read cache, so the
 * {@code @Cacheable} annotation, the Redis configuration and the eviction listener can
 * never drift apart because of a typo.
 */
public final class ProductCache {

    /** Cache holding {@code ProductResponse} values keyed by product id. */
    public static final String NAME = "products";

    private ProductCache() {
    }
}
