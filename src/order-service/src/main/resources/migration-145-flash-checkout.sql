-- Run against mall_order before starting order-service. Historical rows stay NULL.
SET @mall_145_order_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item' AND COLUMN_NAME = 'flash_sale_id'),
    'SELECT 1', 'ALTER TABLE order_item ADD COLUMN flash_sale_id VARCHAR(32) NULL'
);
PREPARE mall_145_order_stmt FROM @mall_145_order_ddl;
EXECUTE mall_145_order_stmt;
DEALLOCATE PREPARE mall_145_order_stmt;

SET @mall_145_owner_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_idempotency' AND COLUMN_NAME = 'reservation_owner'),
    'SELECT 1', 'ALTER TABLE order_idempotency ADD COLUMN reservation_owner VARCHAR(36) NULL'
);
PREPARE mall_145_owner_stmt FROM @mall_145_owner_ddl;
EXECUTE mall_145_owner_stmt;
DEALLOCATE PREPARE mall_145_owner_stmt;
