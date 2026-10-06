-- ShopFlow Phase 1 schema.
-- Flyway runs this exactly once and records it in flyway_schema_history.
-- NEVER edit an applied migration; add V2__..., V3__... instead.

CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'SELLER', 'ADMIN'))
);

CREATE TABLE products (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seller_id      BIGINT        NOT NULL REFERENCES users (id),
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

-- Supports "newest active products first" (the default listing).
CREATE INDEX idx_products_active_created_at ON products (active, created_at DESC);
-- Expression index: our category filter compares lower(category).
CREATE INDEX idx_products_category_lower ON products (lower(category));
-- Supports "products of seller X" and the FK.
CREATE INDEX idx_products_seller_id ON products (seller_id);

CREATE TABLE carts (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_carts_user UNIQUE (user_id)
);

CREATE TABLE cart_items (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cart_id    BIGINT  NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    product_id BIGINT  NOT NULL REFERENCES products (id),
    quantity   INTEGER NOT NULL,
    CONSTRAINT uk_cart_items_cart_product UNIQUE (cart_id, product_id),
    CONSTRAINT ck_cart_items_quantity CHECK (quantity > 0)
);

CREATE TABLE orders (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT        NOT NULL REFERENCES users (id),
    status       VARCHAR(20)   NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL,
    version      BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ck_orders_total CHECK (total_amount >= 0)
);

-- "My orders, newest first" is the most common order query.
CREATE INDEX idx_orders_user_created_at ON orders (user_id, created_at DESC);
CREATE INDEX idx_orders_status ON orders (status);

-- product_id is deliberately NOT a foreign key: an order item is a historical
-- snapshot (name and price at purchase time) and must survive product changes.
CREATE TABLE order_items (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id     BIGINT        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id   BIGINT        NOT NULL,
    product_name VARCHAR(200)  NOT NULL,
    unit_price   NUMERIC(12,2) NOT NULL,
    quantity     INTEGER       NOT NULL,
    line_total   NUMERIC(12,2) NOT NULL,
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
