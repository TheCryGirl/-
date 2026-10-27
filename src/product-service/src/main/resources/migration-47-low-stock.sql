-- ============================================================
-- 老库升级脚本：为已有的 mall_product.sku 补 low_stock_threshold 列
--
-- 背景（Issue #47）：
--   schema.sql 用的是 CREATE TABLE IF NOT EXISTS，
--   对**已存在**的 sku 表不会自动加新列，因此老库需要单独执行本脚本。
--
-- 用法：
--   mysql -u root -p < src/product-service/src/main/resources/migration-47-low-stock.sql
--
-- 幂等性：
--   MySQL 8 不支持 ALTER TABLE ... ADD COLUMN IF NOT EXISTS（MariaDB 才有），
--   所以用 information_schema 判断后动态执行，重复运行不会报错。
-- ============================================================

DROP PROCEDURE IF EXISTS migrate_low_stock_threshold;

DELIMITER $$

CREATE PROCEDURE migrate_low_stock_threshold()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'sku'
          AND COLUMN_NAME  = 'low_stock_threshold'
    ) THEN
        ALTER TABLE sku
            ADD COLUMN low_stock_threshold INT NULL
            COMMENT 'SKU 低库存阈值覆盖值，NULL 表示用全局默认';
        SELECT '已为 sku 表添加 low_stock_threshold 列' AS result;
    ELSE
        SELECT 'sku 表已存在 low_stock_threshold 列，跳过' AS result;
    END IF;
END$$

DELIMITER ;

CALL migrate_low_stock_threshold();
DROP PROCEDURE migrate_low_stock_threshold;
