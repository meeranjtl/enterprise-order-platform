CREATE SCHEMA IF NOT EXISTS analytics;

-- Per-day business rollups, incrementally upserted by the event consumers.
CREATE TABLE daily_metrics
(
    id                 BIGSERIAL PRIMARY KEY,
    metric_date        DATE           NOT NULL,
    total_orders       BIGINT         NOT NULL DEFAULT 0,
    total_revenue      NUMERIC(12, 2) NOT NULL DEFAULT 0,
    avg_order_value    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    completed_orders   BIGINT         NOT NULL DEFAULT 0,
    failed_orders      BIGINT         NOT NULL DEFAULT 0,
    distinct_customers BIGINT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_daily_metrics_date UNIQUE (metric_date)
);

-- Per-day, per-product performance. Product names are intentionally not stored:
-- OrderCreatedEvent.OrderItem carries productId only (Phase 10 decision); the
-- Phase 13 UI enriches names via product-service.
CREATE TABLE product_metrics
(
    id             BIGSERIAL PRIMARY KEY,
    metric_date    DATE           NOT NULL,
    product_id     BIGINT         NOT NULL,
    units_sold     BIGINT         NOT NULL DEFAULT 0,
    revenue        NUMERIC(12, 2) NOT NULL DEFAULT 0,
    times_in_order BIGINT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_metrics_date_product UNIQUE (metric_date, product_id)
);

-- One row per payment outcome per order; the unique order_id is the idempotency
-- anchor that keeps revenue correct across Kafka redeliveries.
CREATE TABLE order_revenue
(
    id             BIGSERIAL PRIMARY KEY,
    order_id       BIGINT         NOT NULL,
    customer_id    BIGINT,
    amount         NUMERIC(12, 2) NOT NULL,
    payment_status VARCHAR(16)    NOT NULL,
    transaction_id VARCHAR(255),
    paid_at        TIMESTAMP,
    created_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_order_revenue_order UNIQUE (order_id)
);
CREATE INDEX idx_order_revenue_paid_at ON order_revenue (paid_at);

-- Fulfillment timings: ordered_at from OrderCreatedEvent, shipped_at /
-- delivered_at from the shipping-events topic (eventType header dispatch).
CREATE TABLE fulfillment_metrics
(
    id                       BIGSERIAL PRIMARY KEY,
    order_id                 BIGINT NOT NULL,
    ordered_at               TIMESTAMP,
    shipped_at               TIMESTAMP,
    delivered_at             TIMESTAMP,
    order_to_ship_seconds    BIGINT,
    order_to_deliver_seconds BIGINT,
    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fulfillment_metrics_order UNIQUE (order_id)
);
