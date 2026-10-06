package com.shopflow.gateway.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Two rate limiters (strict for login/register, normal for the rest of the API) and the
 * matching key resolvers.
 *
 * - RedisRateLimiter MUST be a Spring bean: Spring hands it the Redis template and the Lua
 *   script when it is created. A RedisRateLimiter created with "new" inside the route
 *   definition fails with "RedisRateLimiter is not initialized".
 * - RedisRateLimiter runs a Lua script inside Redis, so "read tokens, refill, take, write
 *   back" is ONE atomic step even with many gateway instances hitting Redis at once.
 * - @Primary: Spring Cloud Gateway's auto-configuration creates its RequestRateLimiter
 *   filter factory with ONE default RateLimiter and ONE default KeyResolver. With two of each,
 *   @Primary tells it which is the default. Our routes pick theirs explicitly by @Qualifier.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    public static final String AUTH_SCOPE = "auth";
    public static final String API_SCOPE = "api";

    @Bean
    public RedisRateLimiter authRateLimiter(RateLimitProperties properties) {
        return redisRateLimiter(properties.auth());
    }

    @Bean
    @Primary
    public RedisRateLimiter apiRateLimiter(RateLimitProperties properties) {
        return redisRateLimiter(properties.api());
    }

    @Bean
    public KeyResolver authKeyResolver() {
        return ClientKeyResolver.byIp(AUTH_SCOPE);
    }

    @Bean
    @Primary
    public KeyResolver apiKeyResolver(JwtSubjectExtractor jwtSubjectExtractor) {
        return ClientKeyResolver.byUserOrIp(API_SCOPE, jwtSubjectExtractor);
    }

    private static RedisRateLimiter redisRateLimiter(RateLimitProperties.Limit limit) {
        return new RedisRateLimiter(limit.replenishRate(), limit.burstCapacity(), limit.requestedTokens());
    }
}
