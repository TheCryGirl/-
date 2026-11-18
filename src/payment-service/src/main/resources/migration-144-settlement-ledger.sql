-- Run on an existing mall_payment schema before starting the updated payment service.
-- New ledger tables are created by the service's schema.sql on startup.
SET @mall_144_commission_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
           AND TABLE_NAME='settlement_item' AND COLUMN_NAME='commission_snapshot'),
    'SELECT 1', 'ALTER TABLE settlement_item ADD COLUMN commission_snapshot TEXT NULL'
);
PREPARE mall_144_commission_stmt FROM @mall_144_commission_ddl;
EXECUTE mall_144_commission_stmt;
DEALLOCATE PREPARE mall_144_commission_stmt;

SET @mall_144_refund_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
           AND TABLE_NAME='settlement_item' AND COLUMN_NAME='refunded_amount'),
    'SELECT 1', 'ALTER TABLE settlement_item ADD COLUMN refunded_amount BIGINT NOT NULL DEFAULT 0'
);
PREPARE mall_144_refund_stmt FROM @mall_144_refund_ddl;
EXECUTE mall_144_refund_stmt;
DEALLOCATE PREPARE mall_144_refund_stmt;
