package com.shopflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.shopflow.product.ProductCache;
import com.shopflow.product.dto.ProductResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** What actually ends up in Redis: key format and JSON value format. */
class RedisCacheConfigTest {

    // Configured like Spring Boot's ObjectMapper (ISO-8601 dates, unknown fields ignored).
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private final ProductResponse product = new ProductResponse(7L, 50L, "Keyboard", "Mechanical",
            "electronics", new BigDecimal("49.99"), 3,
            Instant.parse("2026-01-01T10:00:00Z"), Instant.parse("2026-01-02T11:30:00Z"));

    @Test
    void productsAreStoredAsReadableJsonAndReadBackUnchanged() {
        Jackson2JsonRedisSerializer<ProductResponse> serializer = RedisCacheConfig.productSerializer(objectMapper);

        byte[] stored = serializer.serialize(product);

        assertThat(new String(stored, StandardCharsets.UTF_8))
                .contains("\"name\":\"Keyboard\"")
                .contains("\"createdAt\":\"2026-01-01T10:00:00Z\"")
                .doesNotContain("@class"); // no Java class names stored in Redis
        assertThat(serializer.deserialize(stored)).isEqualTo(product);
    }

    @Test
    void entriesWrittenBeforeAFieldWasRemovedStillDeserialize() {
        Jackson2JsonRedisSerializer<ProductResponse> serializer = RedisCacheConfig.productSerializer(objectMapper);
        String olderShape = """
                {"id":7,"sellerId":50,"name":"Keyboard","description":"Mechanical","category":"electronics",
                 "price":49.99,"stockQuantity":3,"createdAt":"2026-01-01T10:00:00Z",
                 "updatedAt":"2026-01-02T11:30:00Z","legacyField":"ignored"}
                """;

        assertThat(serializer.deserialize(olderShape.getBytes(StandardCharsets.UTF_8))).isEqualTo(product);
    }

    @Test
    void keysAreVersionedAndNamespaced() {
        RedisCacheConfiguration config = RedisCacheConfig.productCacheConfiguration(objectMapper, Duration.ofMinutes(10));

        assertThat(config.getKeyPrefixFor(ProductCache.NAME)).isEqualTo("shopflow:v1:products::");
        assertThat(config.getAllowCacheNullValues()).isFalse();
    }
}
