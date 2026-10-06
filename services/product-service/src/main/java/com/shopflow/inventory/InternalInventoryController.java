package com.shopflow.inventory;

import com.shopflow.inventory.dto.ProductSnapshot;
import com.shopflow.inventory.dto.ReservationResponse;
import com.shopflow.inventory.dto.ReserveStockRequest;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Service-to-service API. Not routed by the gateway (so browsers can't reach it) and
 * protected by the X-Internal-Api-Key header (see InternalApiKeyFilter).
 */
@Hidden
@RestController
@RequestMapping("/internal/products")
public class InternalInventoryController {

    private final InventoryService inventoryService;

    public InternalInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public List<ProductSnapshot> snapshots(@RequestParam List<Long> ids) {
        return inventoryService.snapshots(ids);
    }

    @PostMapping("/reservations")
    public ReservationResponse reserve(@Valid @RequestBody ReserveStockRequest request) {
        return inventoryService.reserve(request);
    }

    @PostMapping("/reservations/{reservationId}/release")
    public ReservationResponse release(@PathVariable UUID reservationId) {
        return inventoryService.release(reservationId);
    }
}
