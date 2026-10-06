package com.shopflow.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(
            OutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    public void publishPending() {
        List<OutboxEvent> events = outboxRepository.findUnpublished(50);

        for (OutboxEvent event : events) {
            try {
                kafkaTemplate
                        .send(event.topic(), event.aggregateId(), event.payload())
                        .get(10, TimeUnit.SECONDS);

                outboxRepository.markPublished(event.id());

                log.info(
                        "Published outbox event id={} topic={} key={}",
                        event.id(),
                        event.topic(),
                        event.eventKey()
                );
            } catch (Exception ex) {
                outboxRepository.markFailed(event.id(), ex.getMessage() == null
                        ? ex.getClass().getSimpleName()
                        : ex.getMessage());

                log.error(
                        "Failed to publish outbox event id={} topic={} attempt={}",
                        event.id(),
                        event.topic(),
                        event.attempts() + 1,
                        ex
                );
            }
        }
    }
}
