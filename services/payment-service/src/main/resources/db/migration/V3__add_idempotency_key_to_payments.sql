ALTER TABLE payments
    ADD COLUMN idempotency_key VARCHAR(255);

CREATE UNIQUE INDEX idx_payments_idempotency_key
    ON payments (idempotency_key);
