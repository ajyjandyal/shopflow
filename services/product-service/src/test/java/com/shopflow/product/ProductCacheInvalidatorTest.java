package com.shopflow.product;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductCacheInvalidatorTest {

    private final Cache cache = mock(Cache.class);
    private final CacheManager cacheManager = mock(CacheManager.class);
    private final ProductCacheInvalidator invalidator = new ProductCacheInvalidator(cacheManager);

    @Test
    void evictsEveryChangedProduct() {
        when(cacheManager.getCache(ProductCache.NAME)).thenReturn(cache);

        invalidator.onProductsChanged(new ProductsChangedEvent(Set.of(1L, 2L)));

        verify(cache).evict(1L);
        verify(cache).evict(2L);
    }

    @Test
    void redisFailureDuringEvictionNeverFailsTheCommittedRequest() {
        when(cacheManager.getCache(ProductCache.NAME)).thenReturn(cache);
        doThrow(new RedisConnectionFailureException("Redis is down")).when(cache).evict(any());

        assertThatCode(() -> invalidator.onProductsChanged(new ProductsChangedEvent(Set.of(1L, 2L))))
                .doesNotThrowAnyException();
        // Stops after the first failure instead of waiting for a timeout per product.
        verify(cache, times(1)).evict(any());
    }
}
