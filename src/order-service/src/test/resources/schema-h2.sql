DROP TABLE IF EXISTS order_compensation;
DROP TABLE IF EXISTS order_idempotency;
DROP TABLE IF EXISTS order_item;
DROP TABLE IF EXISTS `order`;

CREATE TABLE `order` (
    order_id           VARCHAR(32) NOT NULL,
    order_no           VARCHAR(32) NOT NULL,
    buyer_id           VARCHAR(32) NOT NULL,
    seller_id          VARCHAR(32) NOT NULL,
    status             VARCHAR(24) NOT NULL,
    total_amount       BIGINT NOT NULL,
    pay_amount         BIGINT NOT NULL,
    receiver           VARCHAR(32) NOT NULL,
    receiver_phone     VARCHAR(11) NOT NULL,
    address_full       VARCHAR(255) NOT NULL,
    tracking_no        VARCHAR(64),
    expire_at          TIMESTAMP NOT NULL,
    paid_at            TIMESTAMP,
    created_at         TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP NOT NULL,
    created_by         VARCHAR(32),
    updated_by         VARCHAR(32),
    is_deleted         TINYINT NOT NULL DEFAULT 0,
    inventory_released TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (order_id),
    CONSTRAINT uk_order_no UNIQUE (order_no)
);

CREATE TABLE order_item (
    id             BIGINT AUTO_INCREMENT NOT NULL,
    order_id       VARCHAR(32) NOT NULL,
    sku_id         VARCHAR(32) NOT NULL,
    product_id     VARCHAR(32) NOT NULL,
    product_title  VARCHAR(128) NOT NULL,
    spec_name      VARCHAR(64) NOT NULL,
    price_snapshot BIGINT NOT NULL,
    quantity       INT NOT NULL,
    subtotal       BIGINT NOT NULL,
    main_image     VARCHAR(255),
    seller_id      VARCHAR(32) NOT NULL,
    created_at     TIMESTAMP NOT NULL,
    is_deleted     TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_order_sku UNIQUE (order_id, sku_id)
);

CREATE TABLE order_idempotency (
    id           BIGINT AUTO_INCREMENT NOT NULL,
    idem_key     VARCHAR(64) NOT NULL,
    buyer_id     VARCHAR(32) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    order_id     VARCHAR(32) NOT NULL,
    status       VARCHAR(16) NOT NULL,
    created_at   TIMESTAMP NOT NULL,
    updated_at   TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idem_key UNIQUE (idem_key)
);

CREATE TABLE order_compensation (
    id            BIGINT AUTO_INCREMENT NOT NULL,
    order_id      VARCHAR(32) NOT NULL,
    status        VARCHAR(16) NOT NULL,
    retry_count   INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP NOT NULL,
    last_error    VARCHAR(512),
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_compensation_order UNIQUE (order_id)
);
