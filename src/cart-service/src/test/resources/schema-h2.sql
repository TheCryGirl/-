DROP TABLE IF EXISTS cart_item;
DROP TABLE IF EXISTS cart;

CREATE TABLE cart (
    cart_id     VARCHAR(32) NOT NULL,
    user_id     VARCHAR(32) NOT NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(32) DEFAULT NULL,
    updated_by  VARCHAR(32) DEFAULT NULL,
    is_deleted  TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (cart_id),
    CONSTRAINT uk_cart_user UNIQUE (user_id)
);

CREATE TABLE cart_item (
    cart_item_id VARCHAR(32) NOT NULL,
    cart_id      VARCHAR(32) NOT NULL,
    user_id      VARCHAR(32) NOT NULL,
    product_id   VARCHAR(32) NOT NULL,
    sku_id       VARCHAR(32) NOT NULL,
    quantity     INT         NOT NULL,
    is_selected  TINYINT     NOT NULL DEFAULT 1,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   VARCHAR(32) DEFAULT NULL,
    updated_by   VARCHAR(32) DEFAULT NULL,
    is_deleted   TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (cart_item_id),
    CONSTRAINT uk_cart_sku UNIQUE (cart_id, sku_id)
);
