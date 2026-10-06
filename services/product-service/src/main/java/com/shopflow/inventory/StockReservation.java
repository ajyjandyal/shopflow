package com.shopflow.inventory;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A stock reservation, identified by an id chosen BY THE CALLER (order-service).
 * Because the caller picks the id, it can safely repeat the same request after a
 * timeout: the second call finds the existing reservation instead of taking stock twice.
 * That property is called idempotency.
 */
@Entity
@Table(name = "stock_reservations")
public class StockReservation {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @ElementCollection
    @CollectionTable(name = "reservation_lines", joinColumns = @JoinColumn(name = "reservation_id"))
    private List<ReservationLine> lines = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockReservation() {
    }

    private StockReservation(UUID id, ReservationStatus status) {
        this.id = id;
        this.status = status;
    }

    /** Used by tests. In production, PENDING rows are created by an atomic SQL upsert. */
    static StockReservation pending(UUID id) {
        return new StockReservation(id, ReservationStatus.PENDING);
    }

    void markReserved(List<ReservationLine> reservedLines) {
        if (status != ReservationStatus.PENDING) {
            throw new IllegalStateException("Only a PENDING reservation can be marked RESERVED");
        }
        lines.clear();
        lines.addAll(reservedLines);
        status = ReservationStatus.RESERVED;
    }

    void markReleased() {
        status = ReservationStatus.RELEASED;
    }

    public UUID getId() { return id; }
    public ReservationStatus getStatus() { return status; }
    public List<ReservationLine> getLines() { return Collections.unmodifiableList(lines); }
}
