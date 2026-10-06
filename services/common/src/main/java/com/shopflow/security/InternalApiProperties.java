package com.shopflow.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Shared secret for service-to-service calls (e.g. order-service -> product-service).
 * Registered only by services that send or receive internal calls.
 */
@Validated
@ConfigurationProperties(prefix = "app.internal")
public record InternalApiProperties(
        @NotBlank(message = "INTERNAL_API_KEY environment variable must be set")
        @Size(min = 32, message = "INTERNAL_API_KEY must be at least 32 characters")
        String apiKey
) {
    public static final String HEADER = "X-Internal-Api-Key";
}
