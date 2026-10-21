-- 购物车服务数据库表
-- 依据《05-接口契约》第四节与第十节。

CREATE TABLE IF NOT EXISTS cart (
    cart_id     VARCHAR(32)  NOT NULL COMMENT '购物车ID',
    user_id     VARCHAR(32)  NOT NULL COMMENT '用户ID',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by  VARCHAR(32)  DEFAULT NULL COMMENT '创建人',
    updated_by  VARCHAR(32)  DEFAULT NULL COMMENT '更新人',
    is_deleted  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (cart_id),
    -- 一个用户只有一辆购物车
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车';

CREATE TABLE IF NOT EXISTS cart_item (
    cart_item_id VARCHAR(32)  NOT NULL COMMENT '购物车条目ID',
    cart_id      VARCHAR(32)  NOT NULL COMMENT '购物车ID',
    user_id      VARCHAR(32)  NOT NULL COMMENT '用户ID，用于归属校验',
    product_id   VARCHAR(32)  NOT NULL COMMENT '商品ID，展示时按它调商品详情',
    sku_id       VARCHAR(32)  NOT NULL COMMENT 'SKU ID',
    quantity     INT          NOT NULL COMMENT '数量',
    is_selected  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否勾选：0否 1是',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by   VARCHAR(32)  DEFAULT NULL COMMENT '创建人',
    updated_by   VARCHAR(32)  DEFAULT NULL COMMENT '更新人',
    is_deleted   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (cart_item_id),
    -- 同一 SKU 在购物车中只保留一行，重复加购累加数量
    UNIQUE KEY uk_cart_sku (cart_id, sku_id),
    KEY idx_cart_active (cart_id, is_deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车条目';
