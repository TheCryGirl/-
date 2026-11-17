-- ============================================================
-- 老库升级脚本（Issue #135 多店铺合并结算）：
--   1. 新建 main_order 主订单表（CREATE TABLE IF NOT EXISTS 可重复执行）
--   2. 为已有的 mall_order.`order` 补 main_order_id 列与索引
--
-- 背景：
--   schema.sql 用的是 CREATE TABLE IF NOT EXISTS，
--   对**已存在**的 order 表不会自动加新列，因此老库需要单独执行本脚本。
--   历史单店订单 main_order_id 保持 NULL，按单店订单兼容处理。
--
-- 用法：
--   mysql -u root -p < src/order-service/src/main/resources/migration-135-main-order.sql
--
-- 幂等性：
--   MySQL 8 不支持 ALTER TABLE ... ADD COLUMN IF NOT EXISTS（MariaDB 才有），
--   所以用 information_schema 判断后动态执行，重复运行不会报错。
-- ============================================================

CREATE TABLE IF NOT EXISTS main_order (
    main_order_id   VARCHAR(32)  NOT NULL COMMENT '主订单ID',
    main_order_no   VARCHAR(32)  NOT NULL COMMENT '主订单号，对外展示',
    buyer_id        VARCHAR(32)  NOT NULL COMMENT '买家ID',
    status          VARCHAR(24)  NOT NULL COMMENT 'PENDING_PAYMENT/PAID/CANCELLED',
    pay_amount      BIGINT       NOT NULL COMMENT '应付合计，单位分，等于各子订单pay_amount之和',
    paid_at         DATETIME     DEFAULT NULL COMMENT '支付时间（全部子订单支付完成）',
    expire_at       DATETIME     NOT NULL COMMENT '待支付过期时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by      VARCHAR(32)  DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(32)  DEFAULT NULL COMMENT '更新人',
    is_deleted      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (main_order_id),
    UNIQUE KEY uk_main_order_no (main_order_no),
    KEY idx_main_buyer_status (buyer_id, status, created_at),
    KEY idx_main_status_expire (status, expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主订单（跨店合并结算）';

DROP PROCEDURE IF EXISTS migrate_main_order_id;

DELIMITER $$

CREATE PROCEDURE migrate_main_order_id()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'order'
          AND COLUMN_NAME  = 'main_order_id'
    ) THEN
        ALTER TABLE `order`
            ADD COLUMN main_order_id VARCHAR(32) DEFAULT NULL
            COMMENT '主订单ID（#135）；历史单店订单为null' AFTER seller_id;
        SELECT '已为 order 表添加 main_order_id 列' AS result;
    ELSE
        SELECT 'order 表已存在 main_order_id 列，跳过' AS result;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'order'
          AND INDEX_NAME   = 'idx_main_order_id'
    ) THEN
        ALTER TABLE `order` ADD KEY idx_main_order_id (main_order_id);
        SELECT '已为 order 表添加 idx_main_order_id 索引' AS result;
    ELSE
        SELECT 'order 表已存在 idx_main_order_id 索引，跳过' AS result;
    END IF;
END$$

DELIMITER ;

CALL migrate_main_order_id();
DROP PROCEDURE migrate_main_order_id;