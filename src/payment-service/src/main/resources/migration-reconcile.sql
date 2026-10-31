-- ============================================================
-- 老库升级脚本：payment 表新增 reconciled_at（支付后对账时间点）
--
-- 背景：
--   schema.sql 使用 CREATE TABLE IF NOT EXISTS，对已存在的表不会自动加列。
--   新增的 PaymentReconcileJob 需要该列来判断「支付成功但未完成对账」的单。
--
-- 用法：
--   mysql -u root -p mall_payment < src/payment-service/src/main/resources/migration-reconcile.sql
--
-- 幂等性：
--   MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，
--   这里用 information_schema 判断后再动态执行，重复运行不报错。
--
-- 存量数据：
--   加列后所有已有支付单为 NULL，会被对账任务视为「待对账」。
--   为避免历史单被集中重放，对账任务只处理最近 72 小时
--   （payment.reconcile.lookback-hours），更早的历史数据不会自动处理。
-- ============================================================

DROP PROCEDURE IF EXISTS migrate_payment_reconciled_at;

DELIMITER $$

CREATE PROCEDURE migrate_payment_reconciled_at()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'payment'
          AND COLUMN_NAME = 'reconciled_at'
    ) THEN
        ALTER TABLE payment
            ADD COLUMN reconciled_at DATETIME DEFAULT NULL COMMENT '支付后对账完成时间；为空表示待对账' AFTER paid_at;
        SELECT 'payment.reconciled_at 已添加' AS result;
    ELSE
        SELECT 'payment.reconciled_at 已存在，跳过' AS result;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'payment'
          AND INDEX_NAME = 'idx_status_reconciled'
    ) THEN
        ALTER TABLE payment
            ADD INDEX idx_status_reconciled (status, reconciled_at);
        SELECT 'payment.idx_status_reconciled 已添加' AS result;
    ELSE
        SELECT 'payment.idx_status_reconciled 已存在，跳过' AS result;
    END IF;
END$$

DELIMITER ;

CALL migrate_payment_reconciled_at();
DROP PROCEDURE migrate_payment_reconciled_at;
