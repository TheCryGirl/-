-- ============================================================
-- 老库升级脚本（#140）：限时秒杀
--   新增 flash_sale / flash_sale_order 两张表，不改动既有表。
--
-- 用法：
--   mysql -u root -p mall_product < src/product-service/src/main/resources/migration-140-flash-sale.sql
--
-- 幂等性：全部为 CREATE TABLE IF NOT EXISTS，可重复执行。
-- ============================================================

CREATE TABLE IF NOT EXISTS flash_sale (
    sale_id        VARCHAR(32)  NOT NULL COMMENT '秒杀活动ID',
    product_id     VARCHAR(32)  NOT NULL COMMENT '商品ID',
    sku_id         VARCHAR(32)  NOT NULL COMMENT 'SKU ID',
    sale_price     BIGINT       NOT NULL COMMENT '秒杀价，单位分',
    sale_stock     INT          NOT NULL COMMENT '秒杀活动库存',
    limit_per_user INT          NOT NULL DEFAULT 1 COMMENT '每人限购件数',
    start_at       DATETIME     NOT NULL COMMENT '开始时间',
    end_at         DATETIME     NOT NULL COMMENT '结束时间',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (sale_id),
    KEY idx_time (start_at, end_at),
    KEY idx_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动';

CREATE TABLE IF NOT EXISTS flash_sale_order (
    sale_order_id  VARCHAR(32)  NOT NULL COMMENT '秒杀下单记录ID',
    sale_id        VARCHAR(32)  NOT NULL COMMENT '秒杀活动ID',
    user_id        VARCHAR(32)  NOT NULL COMMENT '用户ID',
    quantity       INT          NOT NULL DEFAULT 1 COMMENT '购买件数',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (sale_order_id),
    UNIQUE KEY uk_sale_user (sale_id, user_id),
    KEY idx_sale (sale_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀下单记录';
