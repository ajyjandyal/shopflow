package com.shopflow.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class OutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public OutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(
            UUID id,
            String eventKey,
            String topic,
            String aggregateId,
            String payload
    ) {
        jdbcTemplate.update("""
                INSERT INTO outbox_events
                    (id, event_key, topic, aggregate_id, payload)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (event_key) DO NOTHING
                """,
                id,
                eventKey,
                topic,
                aggregateId,
                payload
        );
    }

    public List<OutboxEvent> findUnpublished(int limit) {
        return jdbcTemplate.query("""
                SELECT id, event_key, topic, aggregate_id,
                       payload, created_at, attempts
                FROM outbox_events
                WHERE published_at IS NULL
                ORDER BY created_at, id
                LIMIT ?
                """,
                (rs, rowNum) -> new OutboxEvent(
                        rs.getObject("id", UUID.class),
                        rs.getString("event_key"),
                        rs.getString("topic"),
                        rs.getString("aggregate_id"),
                        rs.getString("payload"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getInt("attempts")
                ),
                limit
        );
    }

    public void markPublished(UUID id) {
        jdbcTemplate.update("""
                UPDATE outbox_events
                SET published_at = now(),
                    last_error = NULL
                WHERE id = ?
                """, id);
    }

    public void markFailed(UUID id, String error) {
        jdbcTemplate.update("""
                UPDATE outbox_events
                SET attempts = attempts + 1,
                    last_error = ?
                WHERE id = ?
                """,
                error.length() > 2000 ? error.substring(0, 2000) : error,
                id
        );
    }
}
