ALTER TABLE customers
    ADD COLUMN password VARCHAR(255),
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    ADD COLUMN refresh_token_hash VARCHAR(255),
    ADD COLUMN refresh_token_expires_at TIMESTAMP;

CREATE INDEX idx_customer_role ON customers(role);
