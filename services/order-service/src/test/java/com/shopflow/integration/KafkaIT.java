package com.shopflow.integration;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class KafkaIT {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));

    @Test
    void publishesAndConsumesEventUsingRealKafka() throws Exception {
        String bootstrapServers = KAFKA.getBootstrapServers();
        String topic = "shopflow.integration." + UUID.randomUUID();

        Properties producerProperties = new Properties();
        producerProperties.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );
        producerProperties.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName()
        );
        producerProperties.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName()
        );

        Properties consumerProperties = new Properties();
        consumerProperties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );
        consumerProperties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "shopflow-it-" + UUID.randomUUID()
        );
        consumerProperties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );
        consumerProperties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );
        consumerProperties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );

        String eventPayload = "{\"event\":\"order.created.v1\"}";

        try (
                KafkaProducer<String, String> producer =
                        new KafkaProducer<>(producerProperties);

                Consumer<String, String> consumer =
                        new KafkaConsumer<>(consumerProperties)
        ) {
            consumer.subscribe(List.of(topic));

            producer.send(
                    new ProducerRecord<>(
                            topic,
                            "order-1",
                            eventPayload
                    )
            ).get();

            long deadline = System.currentTimeMillis() + 15_000;
            String received = null;

            while (System.currentTimeMillis() < deadline && received == null) {
                ConsumerRecords<String, String> records =
                        consumer.poll(Duration.ofMillis(500));

                for (var record : records) {
                    received = record.value();
                    break;
                }
            }

            assertThat(received).isEqualTo(eventPayload);
        }
    }
}
