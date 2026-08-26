CREATE TABLE IF NOT EXISTS categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(1000),
    price NUMERIC(12, 2) NOT NULL,
    stock_quantity INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    category_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT chk_products_price_positive CHECK (price > 0),
    CONSTRAINT chk_products_stock_non_negative CHECK (stock_quantity >= 0)
);

CREATE INDEX idx_category_name ON categories(name);
CREATE INDEX idx_category_active ON categories(active);
CREATE INDEX idx_product_sku ON products(sku);
CREATE INDEX idx_product_name ON products(name);
CREATE INDEX idx_product_category_id ON products(category_id);
CREATE INDEX idx_product_status ON products(status);
CREATE INDEX idx_product_price ON products(price);
CREATE INDEX idx_product_stock_quantity ON products(stock_quantity);