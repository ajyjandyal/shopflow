package com.shopflow.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ReserveStockRequest(
        @NotNull UUID reservationId,
        @NotEmpty @Size(max = 50) List<@Valid Item> items
) {
    public record Item(@NotNull @Positive Long productId, @NotNull @Min(1) @Max(99) Integer quantity) {
    }
}
