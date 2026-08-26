CREATE SCHEMA IF NOT EXISTS shipping;
CREATE TABLE shipments
(
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT       NOT NULL UNIQUE,
    customer_id     BIGINT,
    tracking_number VARCHAR(40) UNIQUE,
    status          VARCHAR(32)  NOT NULL,
    street          VARCHAR(255),
    building_number VARCHAR(20),
    city            VARCHAR(100),
    state           VARCHAR(100),
    zip_code        VARCHAR(20),
    country         VARCHAR(100),
    packing_list    TEXT,
    shipped_at      TIMESTAMP,
    delivered_at    TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP
);
CREATE INDEX idx_shipments_status ON shipments (status);
CREATE INDEX idx_shipments_tracking_number ON shipments (tracking_number);
