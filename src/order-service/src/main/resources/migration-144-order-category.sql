-- Run against an existing mall_order schema before starting the newer services.
-- Keep unknown historical categories NULL; do not infer them from the current catalog.
SET @mall_144_category_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item'
             AND COLUMN_NAME = 'category_id_snapshot'),
    'SELECT 1',
    'ALTER TABLE order_item ADD COLUMN category_id_snapshot VARCHAR(32) NULL'
);
PREPARE mall_144_category_stmt FROM @mall_144_category_ddl;
EXECUTE mall_144_category_stmt;
DEALLOCATE PREPARE mall_144_category_stmt;

SET @mall_144_completion_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order'
             AND COLUMN_NAME = 'completed_at'),
    'SELECT 1',
    'ALTER TABLE `order` ADD COLUMN completed_at DATETIME NULL'
);
PREPARE mall_144_completion_stmt FROM @mall_144_completion_ddl;
EXECUTE mall_144_completion_stmt;
DEALLOCATE PREPARE mall_144_completion_stmt;

-- Only still-completed legacy orders have the previous completion-time approximation.
UPDATE `order` SET completed_at = updated_at WHERE status = 'COMPLETED' AND completed_at IS NULL;
