DROP TABLE IF EXISTS stock_reservation_item;
DROP TABLE IF EXISTS stock_reservation;
DROP TABLE IF EXISTS sku;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS category;

CREATE TABLE category (
    category_id VARCHAR(32) NOT NULL,
    parent_id   VARCHAR(32),
    name        VARCHAR(32) NOT NULL,
    sort_no     INT NOT NULL DEFAULT 0,
    is_deleted  TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (category_id)
);

CREATE TABLE product (
    product_id  VARCHAR(32) NOT NULL,
    seller_id   VARCHAR(32) NOT NULL,
    title       VARCHAR(128) NOT NULL,
    subtitle    VARCHAR(255),
    main_image  VARCHAR(255),
    category_id VARCHAR(32) NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    sales_count BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    created_by  VARCHAR(32),
    updated_by  VARCHAR(32),
    is_deleted  TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (product_id)
);

CREATE TABLE sku (
    sku_id          VARCHAR(32) NOT NULL,
    product_id      VARCHAR(32) NOT NULL,
    spec_name       VARCHAR(64) NOT NULL,
    price           BIGINT NOT NULL,
    stock_available INT NOT NULL DEFAULT 0,
    stock_locked    INT NOT NULL DEFAULT 0,
    version         INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    is_deleted      TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (sku_id),
    CONSTRAINT uk_product_spec UNIQUE (product_id, spec_name),
    CONSTRAINT chk_sku_stock CHECK (stock_available >= 0 AND stock_locked >= 0)
);

CREATE TABLE stock_reservation (
    reservation_id VARCHAR(32) NOT NULL,
    order_id       VARCHAR(32) NOT NULL,
    status         VARCHAR(16) NOT NULL,
    expire_at      TIMESTAMP NOT NULL,
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP NOT NULL,
    PRIMARY KEY (reservation_id),
    CONSTRAINT uk_order_id UNIQUE (order_id)
);

CREATE TABLE stock_reservation_item (
    id             BIGINT AUTO_INCREMENT NOT NULL,
    reservation_id VARCHAR(32) NOT NULL,
    sku_id         VARCHAR(32) NOT NULL,
    quantity       INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reservation_sku UNIQUE (reservation_id, sku_id)
);
