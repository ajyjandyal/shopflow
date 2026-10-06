package com.shopflow.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Removes changed products from the read cache, but only AFTER the database transaction
 * has committed.
 *
 * Why after commit? If we evicted inside the transaction, a concurrent reader could miss
 * the cache, read the OLD row (the change is not committed yet) and put that stale value
 * back into the cache, where it would stay until the TTL expires. Evicting after commit
 * shrinks that window to almost nothing. If the transaction rolls back, nothing changed,
 * so nothing is evicted.
 *
 * fallbackExecution = true: if an event is ever published outside a transaction, evict
 * immediately instead of silently doing nothing.
 */
@Component
public class ProductCacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheInvalidator.class);

    private final CacheManager cacheManager;

    public ProductCacheInvalidator(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProductsChanged(ProductsChangedEvent event) {
        Cache cache = cacheManager.getCache(ProductCache.NAME);
        if (cache == null) {
            return;
        }
        for (Long productId : event.productIds()) {
            try {
                cache.evict(productId);
                log.debug("Evicted product id={} from cache", productId);
            } catch (RuntimeException ex) {
                // The database change is already committed, so failing the request now would
                // only confuse the caller. The entry expires via its TTL instead. Stop after the
                // first failure: if Redis is down, every further call would just wait for the
                // timeout too.
                log.warn("Could not evict product id(s) {} from cache ({}); stale entries expire via TTL",
                        event.productIds(), ex.toString());
                return;
            }
        }
    }
}
