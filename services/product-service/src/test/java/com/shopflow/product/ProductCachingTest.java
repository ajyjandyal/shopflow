package com.shopflow.product;

import com.shopflow.common.exception.ForbiddenException;
import com.shopflow.common.exception.ResourceNotFoundException;
import com.shopflow.config.CacheConfig;
import com.shopflow.product.dto.ProductRequest;
import com.shopflow.product.dto.ProductResponse;
import com.shopflow.security.AuthenticatedUser;
import com.shopflow.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the REAL caching wiring (CacheConfig, @Cacheable on ProductService, the after-commit
 * ProductCacheInvalidator) in a small Spring context. Redis and PostgreSQL are replaced by an
 * in-memory cache and a mocked repository, so this runs without any infrastructure.
 * (Phase 6 adds Testcontainers tests against a real Redis.)
 */
@SpringJUnitConfig
class ProductCachingTest {

    private static final Long PRODUCT_ID = 1L;
    private static final AuthenticatedUser OWNER = new AuthenticatedUser(50L, "seller@test.com", Role.SELLER);
    private static final AuthenticatedUser OTHER_SELLER = new AuthenticatedUser(51L, "other@test.com", Role.SELLER);
    private static final ProductRequest NEW_DETAILS = new ProductRequest(
            "Keyboard Pro", "Mechanical", "electronics", new BigDecimal("59.99"), 5);

    @Configuration
    @EnableTransactionManagement
    @Import({CacheConfig.class, ProductService.class, ProductCacheInvalidator.class})
    static class TestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(ProductCache.NAME);
        }

        @Bean
        CountingTransactionManager transactionManager() {
            return new CountingTransactionManager();
        }

        @Bean
        ProductRepository productRepository() {
            return mock(ProductRepository.class);
        }
    }

    @Autowired
    private ProductService productService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private CountingTransactionManager transactionManager;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        productsCache().clear();
        transactionManager.reset();
        reset(productRepository);
        Product product = new Product(50L, "Keyboard", "Mechanical", "electronics", new BigDecimal("49.99"), 5);
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
        when(productRepository.findByIdAndActiveTrue(PRODUCT_ID)).thenReturn(Optional.of(product));
    }

    @Test
    void secondReadIsServedFromCacheWithoutOpeningATransaction() {
        ProductResponse first = productService.getById(PRODUCT_ID);
        ProductResponse second = productService.getById(PRODUCT_ID);

        assertThat(second).isEqualTo(first);
        verify(productRepository, times(1)).findByIdAndActiveTrue(PRODUCT_ID);
        // Proves the cache advice runs OUTSIDE the transaction advice (see CacheConfig.order):
        // the cache hit did not begin a transaction, so it never borrowed a DB connection.
        assertThat(transactionManager.begun()).isEqualTo(1);
    }

    @Test
    void updateEvictsTheCachedEntrySoTheNextReadSeesNewData() {
        productService.getById(PRODUCT_ID);

        productService.update(PRODUCT_ID, NEW_DETAILS, OWNER);

        assertThat(productsCache().get(PRODUCT_ID)).isNull();
        ProductResponse reread = productService.getById(PRODUCT_ID);
        assertThat(reread.name()).isEqualTo("Keyboard Pro");
        assertThat(reread.price()).isEqualByComparingTo("59.99");
    }

    @Test
    void failedUpdateKeepsTheCachedEntry() {
        productService.getById(PRODUCT_ID);

        assertThatThrownBy(() -> productService.update(PRODUCT_ID, NEW_DETAILS, OTHER_SELLER))
                .isInstanceOf(ForbiddenException.class);

        assertThat(productsCache().get(PRODUCT_ID)).isNotNull();
    }

    @Test
    void deleteEvictsTheCachedEntry() {
        productService.getById(PRODUCT_ID);

        productService.delete(PRODUCT_ID, OWNER);

        assertThat(productsCache().get(PRODUCT_ID)).isNull();
    }

    @Test
    void stockChangeEvictsOnlyAfterCommit() {
        productService.getById(PRODUCT_ID);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(ProductsChangedEvent.of(PRODUCT_ID));
            assertThat(productsCache().get(PRODUCT_ID)).as("still cached before commit").isNotNull();
        });

        assertThat(productsCache().get(PRODUCT_ID)).as("evicted after commit").isNull();
    }

    @Test
    void rolledBackChangeDoesNotEvict() {
        productService.getById(PRODUCT_ID);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(ProductsChangedEvent.of(PRODUCT_ID));
            status.setRollbackOnly();
        });

        assertThat(productsCache().get(PRODUCT_ID)).isNotNull();
    }

    @Test
    void missingProductsAreNotCached() {
        when(productRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getById(99L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> productService.getById(99L)).isInstanceOf(ResourceNotFoundException.class);

        verify(productRepository, times(2)).findByIdAndActiveTrue(99L);
    }

    private Cache productsCache() {
        return cacheManager.getCache(ProductCache.NAME);
    }

    /**
     * A real (but database-free) transaction manager: Spring runs transaction
     * synchronization with it, so @TransactionalEventListener(AFTER_COMMIT) behaves exactly
     * as in production. It also counts how many transactions were started.
     */
    static class CountingTransactionManager extends AbstractPlatformTransactionManager {

        private final AtomicInteger begun = new AtomicInteger();

        int begun() {
            return begun.get();
        }

        void reset() {
            begun.set(0);
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            begun.incrementAndGet();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
