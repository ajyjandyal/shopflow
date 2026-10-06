package com.shopflow.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Used for both create (POST) and full replace (PUT). */
public record ProductRequest(
        @NotBlank @Size(max = 200)
        String name,

        @Size(max = 2000)
        String description,

        @NotBlank @Size(max = 100)
        String category,

        @NotNull
        @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @Digits(integer = 10, fraction = 2, message = "must have at most 10 integer digits and 2 decimals")
        BigDecimal price,

        @NotNull @Min(0) @Max(1_000_000)
        Integer stockQuantity
) {
}
