CREATE TABLE payment_transactions (
    id         BIGSERIAL PRIMARY KEY,
    payment_id BIGINT      NOT NULL REFERENCES payments (id),
    event_type VARCHAR(64) NOT NULL,
    payload    TEXT,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payment_transactions_payment_id
    ON payment_transactions (payment_id);
