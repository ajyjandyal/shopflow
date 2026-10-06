package com.shopflow.integration;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RedisIT {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));

    @Test
    void storesAndReadsValueUsingRealRedis() {
        String uri = String.format(
                "redis://%s:%d",
                REDIS.getHost(),
                REDIS.getMappedPort(6379)
        );

        RedisClient client = RedisClient.create(uri);

        try (StatefulRedisConnection<String, String> connection = client.connect()) {
            connection.sync().set("shopflow:integration:test", "passed");

            String value = connection.sync().get("shopflow:integration:test");

            assertThat(value).isEqualTo("passed");
        } finally {
            client.shutdown();
        }
    }
}
