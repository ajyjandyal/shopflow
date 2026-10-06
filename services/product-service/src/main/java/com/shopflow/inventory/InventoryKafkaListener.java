package com.shopflow.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.common.event.OrderCancelledEvent;
import com.shopflow.common.event.OrderCreatedEvent;
import com.shopflow.common.exception.ConflictException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class InventoryKafkaListener {

    private static final Logger log =
            LoggerFactory.getLogger(InventoryKafkaListener.class);

    private final ObjectMapper objectMapper;
    private final InventoryEventProcessor processor;
    private final InventoryService inventoryService;

    public InventoryKafkaListener(
            ObjectMapper objectMapper,
            InventoryEventProcessor processor,
            InventoryService inventoryService
    ) {
        this.objectMapper = objectMapper;
        this.processor = processor;
        this.inventoryService = inventoryService;
    }

    @KafkaListener(
            topics = "order.created.v1",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void orderCreated(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment
    ) {
        try {
            OrderCreatedEvent event =
                    objectMapper.readValue(
                            record.value(),
                            OrderCreatedEvent.class
                    );

            try {
                processor.processOrderCreated(event);
            } catch (ConflictException ex) {
                /*
                 * Inventory transaction has rolled back.
                 * Now create the failure event in its own transaction.
                 */
                processor.processReservationFailure(
                        event,
                        ex.getMessage()
                );
            }

            acknowledgment.acknowledge();

            log.info(
                    "Processed order-created event offset={} orderId={}",
                    record.offset(),
                    event.orderId()
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(
                    "Invalid order-created event JSON",
                    ex
            );
        }
    }

    @KafkaListener(
            topics = "order.cancelled.v1",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void orderCancelled(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment
    ) {
        try {
            OrderCancelledEvent event =
                    objectMapper.readValue(
                            record.value(),
                            OrderCancelledEvent.class
                    );

            inventoryService.release(event.reservationId());

            acknowledgment.acknowledge();

            log.info(
                    "Processed order-cancelled event orderId={} reservation={}",
                    event.orderId(),
                    event.reservationId()
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(
                    "Invalid order-cancelled event JSON",
                    ex
            );
        }
    }
}
