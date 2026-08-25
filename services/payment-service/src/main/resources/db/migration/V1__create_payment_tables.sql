CREATE SCHEMA IF NOT EXISTS payment;
CREATE TABLE payments
(
    id             BIGSERIAL PRIMARY KEY,
    order_id       BIGINT         NOT NULL,
    customer_id    BIGINT         NOT NULL,
    amount         NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    status         VARCHAR(20)    NOT NULL,
    method         VARCHAR(30)    NOT NULL,
    transaction_id VARCHAR(255) UNIQUE,
    failure_reason VARCHAR(255),
    retry_count    INTEGER        NOT NULL DEFAULT 0,
    next_retry_at  TIMESTAMP,
    created_at     TIMESTAMP      NOT NULL,
    updated_at     TIMESTAMP      NOT NULL
);
CREATE INDEX idx_payments_retry ON payments (status, next_retry_at);
