-- Run against mall_product before starting product-service.
-- Existing standalone reservations stay unlinked and retain their existing eligibility usage.
SET @mall_145_flash_order_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flash_sale_order' AND COLUMN_NAME = 'order_id'),
    'SELECT 1', 'ALTER TABLE flash_sale_order ADD COLUMN order_id VARCHAR(32) NULL'
);
PREPARE mall_145_flash_order_stmt FROM @mall_145_flash_order_ddl;
EXECUTE mall_145_flash_order_stmt;
DEALLOCATE PREPARE mall_145_flash_order_stmt;

SET @mall_145_flash_price_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flash_sale_order' AND COLUMN_NAME = 'unit_price'),
    'SELECT 1', 'ALTER TABLE flash_sale_order ADD COLUMN unit_price BIGINT NULL'
);
PREPARE mall_145_flash_price_stmt FROM @mall_145_flash_price_ddl;
EXECUTE mall_145_flash_price_stmt;
DEALLOCATE PREPARE mall_145_flash_price_stmt;

SET @mall_145_flash_index_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flash_sale_order' AND INDEX_NAME = 'uk_flash_formal_order'),
    'SELECT 1', 'ALTER TABLE flash_sale_order ADD UNIQUE KEY uk_flash_formal_order (order_id)'
);
PREPARE mall_145_flash_index_stmt FROM @mall_145_flash_index_ddl;
EXECUTE mall_145_flash_index_stmt;
DEALLOCATE PREPARE mall_145_flash_index_stmt;
