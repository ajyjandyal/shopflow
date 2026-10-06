CREATE TABLE outbox_events (
    id            UUID PRIMARY KEY,
    event_key     VARCHAR(200) NOT NULL,
    topic         VARCHAR(200) NOT NULL,
    aggregate_id  VARCHAR(100) NOT NULL,
    payload       TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ,
    attempts      INTEGER NOT NULL DEFAULT 0,
    last_error    VARCHAR(2000),

    CONSTRAINT uk_outbox_event_key UNIQUE (event_key)
);

CREATE INDEX idx_outbox_unpublished
    ON outbox_events (published_at, created_at);
