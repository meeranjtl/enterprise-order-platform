-- Phase 14: local read model of order-events, so InventoryEventConsumer can look up
-- customerId/totalAmount without a synchronous HTTP call to order-service (the prior
-- approach was both unauthenticated and the exact Kafka-only rule Phase 8 forbids).
CREATE TABLE order_snapshots
(
    order_id     BIGINT PRIMARY KEY,
    customer_id  BIGINT         NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL,
    created_at   TIMESTAMP      NOT NULL
);
