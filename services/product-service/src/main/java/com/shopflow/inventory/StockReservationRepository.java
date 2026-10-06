package com.shopflow.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    /**
     * Atomically "claims" a reservation id. If two requests with the same id arrive at the
     * same time, PostgreSQL makes the second INSERT wait until the first transaction
     * finishes, then does nothing. Returns 1 if this call created the row, 0 otherwise.
     */
    @Modifying
    @Query(value = """
            INSERT INTO stock_reservations (id, status, version, created_at, updated_at)
            VALUES (:id, 'PENDING', 0, now(), now())
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int insertPendingIfAbsent(@Param("id") UUID id);

    /** SELECT ... FOR UPDATE on the reservation row: one request per reservation id at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StockReservation r where r.id = :id")
    Optional<StockReservation> findByIdForUpdate(@Param("id") UUID id);
}
