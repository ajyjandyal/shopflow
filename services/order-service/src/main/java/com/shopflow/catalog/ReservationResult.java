package com.shopflow.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ReservationResult(UUID reservationId, String status, List<Line> lines) {

    public record Line(Long productId, String productName, BigDecimal unitPrice, int quantity) {
    }
}
