package com.shopflow.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Type-safe binding of app.jwt.* settings. @Validated makes the application FAIL TO START
 * if JWT_SECRET is missing, which is far better than discovering it on the first login.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank(message = "JWT_SECRET environment variable must be set") String secret,
        @NotNull Duration expiration,
        @NotBlank String issuer
) {
}
