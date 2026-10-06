package com.shopflow.catalog;

import java.util.List;
import java.util.UUID;

public record ReserveStockCommand(UUID reservationId, List<Item> items) {

    public record Item(Long productId, int quantity) {
    }
}
