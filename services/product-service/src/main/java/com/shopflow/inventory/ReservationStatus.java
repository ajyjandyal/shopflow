package com.shopflow.inventory;

/**
 * PENDING  - row exists only inside the transaction that is processing it (never committed as PENDING)
 * RESERVED - stock has been taken out of products.stock_quantity for this reservation
 * RELEASED - stock was given back, OR a release arrived before any reservation ("tombstone")
 */
public enum ReservationStatus {
    PENDING,
    RESERVED,
    RELEASED
}
