-- order-service owns carts and orders. user_id and product_id are plain ids:
-- users and products live in other services' databases, so no foreign keys to them.

CREATE TABLE carts (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_carts_user UNIQUE (user_id)
);

CREATE TABLE cart_items (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cart_id    BIGINT  NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    product_id BIGINT  NOT NULL,
    quantity   INTEGER NOT NULL,
    CONSTRAINT uk_cart_items_cart_product UNIQUE (cart_id, product_id),
    CONSTRAINT ck_cart_items_quantity CHECK (quantity > 0)
);

CREATE TABLE orders (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT        NOT NULL,
    reservation_id UUID          NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    total_amount   NUMERIC(12,2) NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_orders_reservation UNIQUE (reservation_id),
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ck_orders_total CHECK (total_amount >= 0)
);

CREATE INDEX idx_orders_user_created_at ON orders (user_id, created_at DESC);
CREATE INDEX idx_orders_status ON orders (status);

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
