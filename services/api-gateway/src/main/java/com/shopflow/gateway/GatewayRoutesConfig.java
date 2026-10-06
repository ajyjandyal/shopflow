package com.shopflow.gateway;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.BooleanSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.route.builder.UriSpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.springframework.cloud.gateway.support.RouteMetadataUtils.CONNECT_TIMEOUT_ATTR;
import static org.springframework.cloud.gateway.support.RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR;

/**
 * Routing table: which public path goes to which service.
 * /internal/** has no route, so the gateway answers 404 and browsers can't reach it.
 * Each service verifies the JWT itself (zero-trust).
 *
 * Phase 3: every route is rate limited (RequestRateLimiter + Redis token buckets).
 * Login/register get their own, much stricter limit to slow down password guessing and
 * mass account creation. See ratelimit/RateLimitConfig.
 */
@Configuration
public class GatewayRoutesConfig {

    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int RESPONSE_TIMEOUT_MS = 10_000;

    @Bean
    public RouteLocator shopflowRoutes(RouteLocatorBuilder builder,
                                       @Value("${app.services.user-url}") String userServiceUrl,
                                       @Value("${app.services.product-url}") String productServiceUrl,
                                       @Value("${app.services.order-url}") String orderServiceUrl,
                                       @Qualifier("authRateLimiter") RedisRateLimiter authRateLimiter,
                                       @Qualifier("authKeyResolver") KeyResolver authKeyResolver,
                                       @Qualifier("apiRateLimiter") RedisRateLimiter apiRateLimiter,
                                       @Qualifier("apiKeyResolver") KeyResolver apiKeyResolver) {
        return builder.routes()
                .route("auth", r -> limited(r
                        .path("/api/v1/auth/**"), authRateLimiter, authKeyResolver)
                        .uri(userServiceUrl))
                .route("user-service", r -> limited(r
                        .path("/api/v1/users/**"), apiRateLimiter, apiKeyResolver)
                        .uri(userServiceUrl))
                .route("product-service", r -> limited(r
                        .path("/api/v1/products", "/api/v1/products/**"), apiRateLimiter, apiKeyResolver)
                        .uri(productServiceUrl))
                .route("order-service", r -> limited(r
                        .path("/api/v1/cart", "/api/v1/cart/**",
                                "/api/v1/orders", "/api/v1/orders/**",
                                "/api/v1/admin/orders", "/api/v1/admin/orders/**"), apiRateLimiter, apiKeyResolver)
                        .uri(orderServiceUrl))
                .build();
    }

    /** Adds the rate limiter and the per-route timeouts. (metadata must come before uri.) */
    private static UriSpec limited(BooleanSpec route, RedisRateLimiter rateLimiter, KeyResolver keyResolver) {
        return route
                .filters(f -> f.requestRateLimiter(config -> config
                        .setRateLimiter(rateLimiter)
                        .setKeyResolver(keyResolver)))
                .metadata(CONNECT_TIMEOUT_ATTR, CONNECT_TIMEOUT_MS)
                .metadata(RESPONSE_TIMEOUT_ATTR, RESPONSE_TIMEOUT_MS);
    }
}
