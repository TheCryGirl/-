-- ============================================================
-- 老库升级脚本（Issue #53）：为 payment / refund 补 seller_id 快照
--
-- 背景：
--   schema.sql 使用 CREATE TABLE IF NOT EXISTS，对已存在的表不会自动加列。
--   新代码需要在退款列表中按卖家过滤，因此老库需执行本脚本。
--
-- 用法：
--   mysql -u root -p mall_payment < src/payment-service/src/main/resources/migration-53-seller-id.sql
--
-- 幂等性：
--   MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，
--   这里用 information_schema 判断后再动态执行，重复运行不报错。
--
-- 存量数据：
--   本脚本只加列，不跨库回填 seller_id（payment 与 order 分属不同库）。
--   seller_id 为空的历史退款，会在卖家第一次查询退款列表时，
--   由 RefundServiceImpl 按 order_id 调 order-service 惰性补齐；
--   管理员列表不受影响。
-- ============================================================

DROP PROCEDURE IF EXISTS migrate_53_seller_id;

DELIMITER $$

CREATE PROCEDURE migrate_53_seller_id()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'payment'
          AND COLUMN_NAME = 'seller_id'
    ) THEN
        ALTER TABLE payment
            ADD COLUMN seller_id VARCHAR(32) DEFAULT NULL COMMENT '卖家ID快照' AFTER buyer_id;
        SELECT 'payment.seller_id 已添加' AS result;
    ELSE
        SELECT 'payment.seller_id 已存在，跳过' AS result;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'refund'
          AND COLUMN_NAME = 'seller_id'
    ) THEN
        ALTER TABLE refund
            ADD COLUMN seller_id VARCHAR(32) DEFAULT NULL COMMENT '卖家ID快照' AFTER buyer_id;
        SELECT 'refund.seller_id 已添加' AS result;
    ELSE
        SELECT 'refund.seller_id 已存在，跳过' AS result;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'refund'
          AND INDEX_NAME = 'idx_seller_status_created'
    ) THEN
        ALTER TABLE refund
            ADD INDEX idx_seller_status_created (seller_id, status, created_at);
        SELECT 'refund.idx_seller_status_created 已添加' AS result;
    ELSE
        SELECT 'refund.idx_seller_status_created 已存在，跳过' AS result;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'payment'
          AND INDEX_NAME = 'idx_seller_created'
    ) THEN
        ALTER TABLE payment
            ADD INDEX idx_seller_created (seller_id, created_at);
        SELECT 'payment.idx_seller_created 已添加' AS result;
    ELSE
        SELECT 'payment.idx_seller_created 已存在，跳过' AS result;
    END IF;
END$$

DELIMITER ;

CALL migrate_53_seller_id();
DROP PROCEDURE migrate_53_seller_id;
