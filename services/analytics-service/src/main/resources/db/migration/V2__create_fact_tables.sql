-- Per-event fact anchors. Rollup tables (daily_metrics, product_metrics) are
-- RECOMPUTED from these facts on every event instead of incrementally bumped:
-- with at-least-once Kafka delivery an incremental counter would double-count
-- on redelivery, whereas recompute-from-facts always converges to the truth
-- regardless of redelivery or event ordering.

CREATE TABLE order_facts
(
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT         NOT NULL,
    customer_id  BIGINT,
    total_amount NUMERIC(12, 2),
    ordered_date DATE           NOT NULL,
    ordered_at   TIMESTAMP      NOT NULL,
    created_at   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_order_facts_order UNIQUE (order_id)
);
CREATE INDEX idx_order_facts_ordered_date ON order_facts (ordered_date);

CREATE TABLE order_item_facts
(
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT         NOT NULL,
    product_id   BIGINT         NOT NULL,
    quantity     INTEGER        NOT NULL,
    unit_price   NUMERIC(12, 2) NOT NULL,
    ordered_date DATE           NOT NULL,
    created_at   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_order_item_facts_order_product UNIQUE (order_id, product_id)
);
CREATE INDEX idx_order_item_facts_date_product ON order_item_facts (ordered_date, product_id);
