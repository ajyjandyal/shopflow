package com.shopflow.config;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Turns on Spring's cache abstraction ({@code @Cacheable}).
 *
 * order = LOWEST_PRECEDENCE - 1: Spring Boot's transaction advice runs at
 * LOWEST_PRECEDENCE. A smaller number means "outer", so the cache check happens BEFORE a
 * transaction is opened. A cache hit therefore never borrows a database connection, and a
 * miss stores the value only after the read transaction has finished.
 *
 * This class deliberately knows nothing about Redis (see RedisCacheConfig), so tests can
 * reuse it with an in-memory cache.
 */
@Configuration
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 1)
public class CacheConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new FailOpenCacheErrorHandler();
    }
}
