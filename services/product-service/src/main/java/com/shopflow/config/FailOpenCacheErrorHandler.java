package com.shopflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * "Fail open": if the cache itself fails (Redis down, timeout, corrupt entry), log it and
 * carry on as if it were a cache miss, so the request is served from PostgreSQL.
 *
 * Without this, Spring's default handler rethrows the exception and a cache outage would
 * turn every product read into a 500 error, even though the database is perfectly fine.
 * Stack traces are omitted on purpose: during an outage this fires on every request.
 */
public class FailOpenCacheErrorHandler implements CacheErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(FailOpenCacheErrorHandler.class);

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache GET failed (cache={}, key={}): {}. Falling back to the database.",
                cache.getName(), key, exception.toString());
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("Cache PUT failed (cache={}, key={}): {}. Response is still returned.",
                cache.getName(), key, exception.toString());
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache EVICT failed (cache={}, key={}): {}. Entry will expire via TTL.",
                cache.getName(), key, exception.toString());
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("Cache CLEAR failed (cache={}): {}", cache.getName(), exception.toString());
    }
}
