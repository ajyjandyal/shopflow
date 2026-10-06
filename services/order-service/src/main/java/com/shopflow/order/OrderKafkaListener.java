package com.shopflow.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.common.event.StockReservationFailedEvent;
import com.shopflow.common.event.StockReservedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class OrderKafkaListener {

    private static final Logger log =
            LoggerFactory.getLogger(OrderKafkaListener.class);

    private final ObjectMapper objectMapper;
    private final OrderEventProcessor processor;

    public OrderKafkaListener(
            ObjectMapper objectMapper,
            OrderEventProcessor processor
    ) {
        this.objectMapper = objectMapper;
        this.processor = processor;
    }

    @KafkaListener(
            topics = "stock.reserved.v1",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void stockReserved(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment
    ) {
        try {
            StockReservedEvent event =
                    objectMapper.readValue(
                            record.value(),
                            StockReservedEvent.class
                    );

            processor.handleReserved(event);
            acknowledgment.acknowledge();

            log.info(
                    "Processed stock-reserved event offset={} orderId={}",
                    record.offset(),
                    event.orderId()
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(
                    "Invalid stock-reserved event JSON",
                    ex
            );
        }
    }

    @KafkaListener(
            topics = "stock.reservation.failed.v1",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void stockReservationFailed(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment
    ) {
        try {
            StockReservationFailedEvent event =
                    objectMapper.readValue(
                            record.value(),
                            StockReservationFailedEvent.class
                    );

            processor.handleReservationFailed(event);
            acknowledgment.acknowledge();

            log.info(
                    "Processed stock-reservation-failed event offset={} orderId={}",
                    record.offset(),
                    event.orderId()
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(
                    "Invalid stock-reservation-failed event JSON",
                    ex
            );
        }
    }
}
