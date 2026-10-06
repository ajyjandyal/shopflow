package com.shopflow.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.common.event.OrderCreatedEvent;
import com.shopflow.common.event.OrderCreatedItem;
import com.shopflow.common.event.StockReservationFailedEvent;
import com.shopflow.common.event.StockReservedEvent;
import com.shopflow.common.exception.ConflictException;
import com.shopflow.inventory.dto.ReservationResponse;
import com.shopflow.inventory.dto.ReserveStockRequest;
import com.shopflow.outbox.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryEventProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(InventoryEventProcessor.class);

    private static final String RESERVED_TOPIC = "stock.reserved.v1";
    private static final String FAILED_TOPIC = "stock.reservation.failed.v1";

    private final InventoryService inventoryService;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public InventoryEventProcessor(
            InventoryService inventoryService,
            OutboxRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.inventoryService = inventoryService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void processOrderCreated(OrderCreatedEvent event) {
        ReserveStockRequest request = new ReserveStockRequest(
                event.reservationId(),
                event.items().stream()
                        .map(item -> new ReserveStockRequest.Item(
                                item.productId(),
                                item.quantity()
                        ))
                        .toList()
        );

        ReservationResponse result =
                inventoryService.reserve(request);

        StockReservedEvent reservedEvent =
                new StockReservedEvent(
                        UUID.randomUUID(),
                        1,
                        Instant.now(),
                        event.orderId(),
                        event.reservationId()
                );

        outboxRepository.insert(
                reservedEvent.eventId(),
                "stock-reserved:" + event.reservationId(),
                RESERVED_TOPIC,
                String.valueOf(event.orderId()),
                serialize(reservedEvent)
        );

        log.info(
                "Processed OrderCreated orderId={} reservation={} status={}",
                event.orderId(),
                event.reservationId(),
                result.status()
        );
    }

    @Transactional
    public void processReservationFailure(
            OrderCreatedEvent event,
            String reason
    ) {
        StockReservationFailedEvent failedEvent =
                new StockReservationFailedEvent(
                        UUID.randomUUID(),
                        1,
                        Instant.now(),
                        event.orderId(),
                        event.reservationId(),
                        reason
                );

        outboxRepository.insert(
                failedEvent.eventId(),
                "stock-reservation-failed:" + event.reservationId(),
                FAILED_TOPIC,
                String.valueOf(event.orderId()),
                serialize(failedEvent)
        );

        log.warn(
                "Stock reservation failed for orderId={} reservation={} reason={}",
                event.orderId(),
                event.reservationId(),
                reason
        );
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Could not serialize Kafka event",
                    ex
            );
        }
    }
}
