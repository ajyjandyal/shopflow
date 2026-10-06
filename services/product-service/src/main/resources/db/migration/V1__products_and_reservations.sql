-- product-service owns products and stock reservations.
-- seller_id is NOT a foreign key any more: users live in a different database
-- (user-service). Cross-service references are plain ids.

CREATE TABLE products (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seller_id      BIGINT        NOT NULL,
    name           VARCHAR(200)  NOT NULL,
    description    VARCHAR(2000),
    category       VARCHAR(100)  NOT NULL,
    price          NUMERIC(12,2) NOT NULL,
    stock_quantity INTEGER       NOT NULL,
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_products_price CHECK (price > 0),
    CONSTRAINT ck_products_stock CHECK (stock_quantity >= 0)
);

CREATE INDEX idx_products_active_created_at ON products (active, created_at DESC);
CREATE INDEX idx_products_category_lower ON products (lower(category));
CREATE INDEX idx_products_seller_id ON products (seller_id);

-- id is chosen by the caller (order-service), which is what makes reserve() idempotent.
CREATE TABLE stock_reservations (
    id         UUID PRIMARY KEY,
    status     VARCHAR(20) NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_stock_reservations_status CHECK (status IN ('PENDING', 'RESERVED', 'RELEASED'))
);

CREATE TABLE reservation_lines (
    reservation_id UUID          NOT NULL REFERENCES stock_reservations (id) ON DELETE CASCADE,
    product_id     BIGINT        NOT NULL REFERENCES products (id),
    product_name   VARCHAR(200)  NOT NULL,
    unit_price     NUMERIC(12,2) NOT NULL,
    quantity       INTEGER       NOT NULL,
    PRIMARY KEY (reservation_id, product_id),
    CONSTRAINT ck_reservation_lines_quantity CHECK (quantity > 0)
);
