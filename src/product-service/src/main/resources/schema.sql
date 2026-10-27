-- 商品服务数据库表
-- 依据《05-接口契约》第三节与第十节。

CREATE TABLE IF NOT EXISTS category (
    category_id VARCHAR(32)  NOT NULL COMMENT '分类ID',
    parent_id   VARCHAR(32)  DEFAULT NULL COMMENT '父分类ID',
    name        VARCHAR(32)  NOT NULL COMMENT '分类名称',
    sort_no     INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    is_deleted  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类';

CREATE TABLE IF NOT EXISTS product (
    product_id  VARCHAR(32)  NOT NULL COMMENT '商品ID',
    seller_id   VARCHAR(32)  NOT NULL COMMENT '卖家ID',
    title       VARCHAR(128) NOT NULL COMMENT '商品标题',
    subtitle    VARCHAR(255) DEFAULT NULL COMMENT '商品副标题',
    main_image  VARCHAR(255) DEFAULT NULL COMMENT '主图URL',
    category_id VARCHAR(32)  NOT NULL COMMENT '分类ID',
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ON_SALE/OFF_SHELF/REJECTED',
    sales_count BIGINT       NOT NULL DEFAULT 0 COMMENT '累计销量',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by  VARCHAR(32)  DEFAULT NULL COMMENT '创建人',
    updated_by  VARCHAR(32)  DEFAULT NULL COMMENT '更新人',
    is_deleted  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (product_id),
    KEY idx_status_category (status, category_id, created_at),
    KEY idx_seller_status (seller_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品';

CREATE TABLE IF NOT EXISTS sku (
    sku_id          VARCHAR(32) NOT NULL COMMENT 'SKU ID',
    product_id      VARCHAR(32) NOT NULL COMMENT '商品ID',
    spec_name       VARCHAR(64) NOT NULL COMMENT '规格名称',
    price           BIGINT      NOT NULL COMMENT '价格，单位分',
    stock_available INT         NOT NULL DEFAULT 0 COMMENT '可售库存',
    stock_locked    INT         NOT NULL DEFAULT 0 COMMENT '预占库存',
    version         INT         NOT NULL DEFAULT 0 COMMENT '版本号',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted      TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    low_stock_threshold INT     NULL COMMENT 'SKU 低库存阈值覆盖值，NULL 表示用全局默认',
    PRIMARY KEY (sku_id),
    UNIQUE KEY uk_product_spec (product_id, spec_name),
    KEY idx_product (product_id),
    CONSTRAINT chk_sku_stock CHECK (stock_available >= 0 AND stock_locked >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SKU 与库存';

CREATE TABLE IF NOT EXISTS stock_reservation (
    reservation_id VARCHAR(32) NOT NULL COMMENT '预占单ID',
    order_id       VARCHAR(32) NOT NULL COMMENT '订单ID',
    status         VARCHAR(16) NOT NULL COMMENT 'LOCKED/CONFIRMED/RELEASED',
    expire_at      DATETIME    NOT NULL COMMENT '预占过期时间',
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (reservation_id),
    UNIQUE KEY uk_order_id (order_id),
    KEY idx_status_expire (status, expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存预占单头（order_id 幂等）';

CREATE TABLE IF NOT EXISTS stock_reservation_item (
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '明细ID',
    reservation_id VARCHAR(32) NOT NULL COMMENT '预占单ID',
    sku_id         VARCHAR(32) NOT NULL COMMENT 'SKU ID',
    quantity       INT         NOT NULL COMMENT '预占数量',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reservation_sku (reservation_id, sku_id),
    KEY idx_reservation (reservation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存预占明细';
CREATE TABLE IF NOT EXISTS product_setting (
    setting_key   VARCHAR(64) NOT NULL,
    setting_value INT NOT NULL,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品库存配置';