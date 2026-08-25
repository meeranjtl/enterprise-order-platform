-- Repeatable migration: creates the outbox_events table for the transactional outbox pattern.
--
-- Shipped inside shared-library, so it is picked up by every service's Flyway run and
-- applied into that service's own schema (orders, inventory, payment, ...). Each service
-- already owns a versioned V1 schema migration, and Flyway requires version numbers to be
-- unique across ALL classpath locations — a shared V1 script would collide with every
-- service and abort startup. Repeatable migrations (R__) carry no version, run after the
-- versioned ones, and re-run only when their checksum changes.

CREATE TABLE IF NOT EXISTS outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    kafka_topic VARCHAR(255) NOT NULL,
    kafka_key VARCHAR(255),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_published ON outbox_events(published);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate_id ON outbox_events(aggregate_id);
CREATE INDEX IF NOT EXISTS idx_outbox_created_at ON outbox_events(created_at);
