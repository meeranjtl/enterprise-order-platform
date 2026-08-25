CREATE SCHEMA IF NOT EXISTS inventory;
CREATE TABLE inventory.inventory (id BIGSERIAL PRIMARY KEY, product_id BIGINT NOT NULL UNIQUE, total_quantity INTEGER NOT NULL CHECK (total_quantity >= 0), reserved_quantity INTEGER NOT NULL CHECK (reserved_quantity >= 0), available_quantity INTEGER NOT NULL CHECK (available_quantity >= 0), last_updated TIMESTAMP NOT NULL);
CREATE TABLE inventory.inventory_transactions (id BIGSERIAL PRIMARY KEY, product_id BIGINT NOT NULL, order_id BIGINT, type VARCHAR(16) NOT NULL, quantity INTEGER NOT NULL, reason VARCHAR(255), created_at TIMESTAMP NOT NULL);
CREATE INDEX idx_inventory_transactions_product_id ON inventory.inventory_transactions(product_id);
CREATE TABLE inventory.idempotency_records (id BIGSERIAL PRIMARY KEY, operation VARCHAR(32) NOT NULL, idempotency_key VARCHAR(255) NOT NULL, transaction_id BIGINT NOT NULL, created_at TIMESTAMP NOT NULL, CONSTRAINT uk_idempotency_operation_key UNIQUE (operation, idempotency_key));
