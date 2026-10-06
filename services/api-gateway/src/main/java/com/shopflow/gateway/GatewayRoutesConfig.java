package com.shopflow.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.springframework.cloud.gateway.support.RouteMetadataUtils.CONNECT_TIMEOUT_ATTR;
import static org.springframework.cloud.gateway.support.RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR;

/**
 * Routing table: which public path goes to which service.
 * /internal/** has no route, so the gateway answers 404 and browsers can't reach it.
 * Each service verifies the JWT itself (zero-trust).
 */
@Configuration
public class GatewayRoutesConfig {

    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int RESPONSE_TIMEOUT_MS = 10_000;

    @Bean
    public RouteLocator shopflowRoutes(RouteLocatorBuilder builder,
                                       @Value("${app.services.user-url}") String userServiceUrl,
                                       @Value("${app.services.product-url}") String productServiceUrl,
                                       @Value("${app.services.order-url}") String orderServiceUrl) {
        return builder.routes()
                .route("user-service", r -> r
                        .path("/api/v1/auth/**", "/api/v1/users/**")
                        .metadata(CONNECT_TIMEOUT_ATTR, CONNECT_TIMEOUT_MS)
                        .metadata(RESPONSE_TIMEOUT_ATTR, RESPONSE_TIMEOUT_MS)
                        .uri(userServiceUrl))
                .route("product-service", r -> r
                        .path("/api/v1/products", "/api/v1/products/**")
                        .metadata(CONNECT_TIMEOUT_ATTR, CONNECT_TIMEOUT_MS)
                        .metadata(RESPONSE_TIMEOUT_ATTR, RESPONSE_TIMEOUT_MS)
                        .uri(productServiceUrl))
                .route("order-service", r -> r
                        .path("/api/v1/cart", "/api/v1/cart/**",
                                "/api/v1/orders", "/api/v1/orders/**",
                                "/api/v1/admin/orders", "/api/v1/admin/orders/**")
                        .metadata(CONNECT_TIMEOUT_ATTR, CONNECT_TIMEOUT_MS)
                        .metadata(RESPONSE_TIMEOUT_ATTR, RESPONSE_TIMEOUT_MS)
                        .uri(orderServiceUrl))
                .build();
    }
}
