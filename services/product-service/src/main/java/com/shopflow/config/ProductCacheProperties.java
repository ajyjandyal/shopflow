package com.shopflow.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Binds app.cache.* (see application.yml). Startup fails if the TTL is missing or not positive. */
@Validated
@ConfigurationProperties(prefix = "app.cache")
public record ProductCacheProperties(@NotNull Duration productTtl) {

    public ProductCacheProperties {
        if (productTtl != null && (productTtl.isZero() || productTtl.isNegative())) {
            throw new IllegalArgumentException("app.cache.product-ttl must be positive, e.g. PT10M");
        }
    }
}
