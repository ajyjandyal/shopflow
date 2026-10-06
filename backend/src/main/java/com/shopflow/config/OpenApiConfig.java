package com.shopflow.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Adds an "Authorize" button to Swagger UI so you can paste a JWT once and call
 * protected endpoints directly from the browser.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "ShopFlow API",
                version = "v1",
                description = "E-commerce and order processing API (Phase 1: modular monolith)"),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {
}
