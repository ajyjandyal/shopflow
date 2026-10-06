package com.shopflow.inventory;

import com.shopflow.common.exception.BadRequestException;
import com.shopflow.common.exception.ConflictException;
import com.shopflow.inventory.dto.ProductSnapshot;
import com.shopflow.inventory.dto.ReservationResponse;
import com.shopflow.inventory.dto.ReserveStockRequest;
import com.shopflow.product.Product;
import com.shopflow.product.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stock operations called by other services (never by browsers).
 *
 * Both reserve() and release() are IDEMPOTENT: calling them twice with the same
 * reservation id has the same effect as calling them once. This is what makes it safe
 * for order-service to retry after a timeout, or to release "just in case".
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    static final int MAX_SNAPSHOT_IDS = 100;

    private final ProductRepository productRepository;
    private final StockReservationRepository reservationRepository;

    public InventoryService(ProductRepository productRepository,
                            StockReservationRepository reservationRepository) {
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductSnapshot> snapshots(Collection<Long> productIds) {
        if (productIds.size() > MAX_SNAPSHOT_IDS) {
            throw new BadRequestException("At most " + MAX_SNAPSHOT_IDS + " product ids per request");
        }
        return productRepository.findAllById(productIds).stream().map(ProductSnapshot::from).toList();
    }

    @Transactional
    public ReservationResponse reserve(ReserveStockRequest request) {
        StockReservation reservation = claim(request.reservationId());

        switch (reservation.getStatus()) {
            case RESERVED -> {
                // Same request seen before (e.g. a retry after a timeout): replay the result.
                log.info("Reservation {} already exists; returning it (idempotent replay)", reservation.getId());
                return ReservationResponse.from(reservation);
            }
            case RELEASED -> throw new ConflictException(
                    "Reservation " + reservation.getId() + " was already cancelled");
            case PENDING -> {
                // First time we see this id: do the real work below.
            }
        }

        // Merge duplicate product ids and sort them (TreeMap) so rows are locked in a fixed order.
        Map<Long, Integer> quantities = request.items().stream()
                .collect(Collectors.toMap(ReserveStockRequest.Item::productId,
                        ReserveStockRequest.Item::quantity, Integer::sum, TreeMap::new));

        Map<Long, Product> products = productRepository.findAllByIdForUpdate(quantities.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<ReservationLine> lines = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Product product = products.get(entry.getKey());
            if (product == null || !product.isActive()) {
                throw new ConflictException("Product " + entry.getKey() + " is no longer available");
            }
            product.decreaseStock(entry.getValue()); // throws ConflictException if insufficient
            lines.add(new ReservationLine(product.getId(), product.getName(), product.getPrice(), entry.getValue()));
        }

        reservation.markReserved(lines);
        log.info("Reservation {} RESERVED: {} product(s)", reservation.getId(), lines.size());
        return ReservationResponse.from(reservation);
        // If anything above threw, the transaction rolls back: stock is restored AND the
        // PENDING row disappears, so a later retry starts cleanly.
    }

    @Transactional
    public ReservationResponse release(java.util.UUID reservationId) {
        StockReservation reservation = claim(reservationId);

        switch (reservation.getStatus()) {
            case RELEASED -> {
                log.info("Reservation {} already released (idempotent no-op)", reservationId);
                return ReservationResponse.from(reservation);
            }
            case PENDING -> {
                // Release arrived but there was never a reservation (e.g. the reserve call timed
                // out before reaching us). Store a RELEASED "tombstone" so that if the delayed
                // reserve request arrives later, it is rejected instead of taking stock forever.
                reservation.markReleased();
                log.warn("Release for unknown reservation {}: stored tombstone", reservationId);
                return ReservationResponse.from(reservation);
            }
            case RESERVED -> {
                Map<Long, Integer> quantities = reservation.getLines().stream()
                        .collect(Collectors.toMap(ReservationLine::getProductId, ReservationLine::getQuantity,
                                Integer::sum, TreeMap::new));
                for (Product product : productRepository.findAllByIdForUpdate(quantities.keySet())) {
                    product.increaseStock(quantities.get(product.getId()));
                }
                reservation.markReleased();
                log.info("Reservation {} RELEASED: stock returned", reservationId);
                return ReservationResponse.from(reservation);
            }
            default -> throw new IllegalStateException("Unknown status " + reservation.getStatus());
        }
    }

    /**
     * Makes sure a row exists for this id, then locks it. Concurrent requests for the same
     * reservation id are processed strictly one after another.
     */
    private StockReservation claim(java.util.UUID reservationId) {
        reservationRepository.insertPendingIfAbsent(reservationId);
        return reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new IllegalStateException("Reservation row missing after upsert: " + reservationId));
    }
}
