package com.shopflow.inventory.dto;

import com.shopflow.inventory.ReservationLine;
import com.shopflow.inventory.ReservationStatus;
import com.shopflow.inventory.StockReservation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(UUID reservationId, ReservationStatus status, List<Line> lines) {

    public record Line(Long productId, String productName, BigDecimal unitPrice, int quantity) {
        static Line from(ReservationLine line) {
            return new Line(line.getProductId(), line.getProductName(), line.getUnitPrice(), line.getQuantity());
        }
    }

    public static ReservationResponse from(StockReservation reservation) {
        return new ReservationResponse(reservation.getId(), reservation.getStatus(),
                reservation.getLines().stream().map(Line::from).toList());
    }
}
