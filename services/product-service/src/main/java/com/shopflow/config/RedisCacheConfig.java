package com.shopflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.product.ProductCache;
import com.shopflow.product.dto.ProductResponse;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * How the "products" cache is stored in Redis. Spring Boot builds the RedisCacheManager
 * (because spring.cache.type=redis); we only customise this one cache.
 *
 * Choices:
 * - JSON values, typed to ProductResponse. Readable in redis-cli, language-neutral, and no
 *   Java serialization (which breaks when a class changes and is a known security risk).
 *   Using a TYPED serializer avoids storing Java class names in Redis and avoids the
 *   records-plus-default-typing pitfall of the generic serializer.
 * - Spring Boot's ObjectMapper, so Instant/BigDecimal are written exactly like API responses.
 * - Versioned key prefix "shopflow:v1:products::{id}". If ProductResponse ever changes in an
 *   incompatible way, bump v1 to v2 and old entries are simply ignored until they expire.
 * - TTL as a safety net, so even a missed eviction can't serve stale data forever.
 * - Null values are not cached: a missing product throws 404 and is never stored.
 */
@Configuration
@EnableConfigurationProperties(ProductCacheProperties.class)
public class RedisCacheConfig {

    static final String KEY_PREFIX = "shopflow:v1:";

    @Bean
    public RedisCacheManagerBuilderCustomizer productCacheCustomizer(ObjectMapper objectMapper,
                                                                     ProductCacheProperties properties) {
        RedisCacheConfiguration products = productCacheConfiguration(objectMapper, properties.productTtl());
        return builder -> builder.withCacheConfiguration(ProductCache.NAME, products);
    }

    static RedisCacheConfiguration productCacheConfiguration(ObjectMapper objectMapper, Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> KEY_PREFIX + cacheName + "::")
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(productSerializer(objectMapper)));
    }

    static Jackson2JsonRedisSerializer<ProductResponse> productSerializer(ObjectMapper objectMapper) {
        return new Jackson2JsonRedisSerializer<>(objectMapper, ProductResponse.class);
    }
}
