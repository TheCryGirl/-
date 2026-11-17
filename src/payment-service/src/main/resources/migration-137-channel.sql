-- ============================================================
-- 老库升级脚本（#137）：refund 表加渠道退款字段 + 新增回调/对账表
--
-- 用法：
--   mysql -u root -p mall_payment < src/payment-service/src/main/resources/migration-137-channel.sql
--
-- 幂等性：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，
-- 用 information_schema 判断后动态执行；新表用 CREATE TABLE IF NOT EXISTS。
-- ============================================================

DROP PROCEDURE IF EXISTS migrate_137_refund_channel;

DELIMITER $$

CREATE PROCEDURE migrate_137_refund_channel()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refund'
          AND COLUMN_NAME = 'channel_refund_no'
    ) THEN
        ALTER TABLE refund
            ADD COLUMN channel_refund_no VARCHAR(64) DEFAULT NULL COMMENT '渠道退款单号（#137 原路退款）' AFTER status;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refund'
          AND COLUMN_NAME = 'channel_error'
    ) THEN
        ALTER TABLE refund
            ADD COLUMN channel_error VARCHAR(255) DEFAULT NULL COMMENT '渠道退款失败原因（不含凭据）' AFTER channel_refund_no;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refund'
          AND COLUMN_NAME = 'refunded_at'
    ) THEN
        ALTER TABLE refund
            ADD COLUMN refunded_at DATETIME DEFAULT NULL COMMENT '渠道退款到账时间' AFTER channel_error;
    END IF;
END$$

DELIMITER ;

CALL migrate_137_refund_channel();
DROP PROCEDURE migrate_137_refund_channel;

CREATE TABLE IF NOT EXISTS refund_callback (
    callback_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '回调流水ID',
    channel         VARCHAR(16)  NOT NULL COMMENT '渠道：ALIPAY/MOCK',
    notify_id       VARCHAR(128) NOT NULL COMMENT '渠道通知唯一标识',
    refund_id       VARCHAR(32)  NOT NULL COMMENT '退款单ID',
    raw_payload     TEXT         COMMENT '回调原始参数（JSON）',
    sign_verified   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '验签结果：0失败 1通过',
    amount          BIGINT       NOT NULL COMMENT '回调退款金额，单位分',
    process_status  VARCHAR(16)  NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/PROCESSED/DUPLICATE/AMOUNT_MISMATCH',
    error_message   VARCHAR(255) DEFAULT NULL COMMENT '处理失败原因',
    received_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
    PRIMARY KEY (callback_id),
    UNIQUE KEY uk_channel_notify (channel, notify_id),
    KEY idx_refund_id (refund_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款回调流水';

CREATE TABLE IF NOT EXISTS reconcile_batch (
    batch_id        VARCHAR(32)  NOT NULL COMMENT '批次ID',
    channel         VARCHAR(16)  NOT NULL COMMENT '对账渠道',
    file_name       VARCHAR(255) DEFAULT NULL COMMENT '账单文件名',
    total_rows      INT          NOT NULL DEFAULT 0 COMMENT '账单有效行数',
    diff_count      INT          NOT NULL DEFAULT 0 COMMENT '差异条数',
    imported_by     VARCHAR(32)  NOT NULL COMMENT '导入人（管理员）',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '导入时间',
    PRIMARY KEY (batch_id),
    KEY idx_channel_created (channel, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道对账批次';

CREATE TABLE IF NOT EXISTS reconcile_difference (
    difference_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '差异ID',
    batch_id        VARCHAR(32)  NOT NULL COMMENT '批次ID',
    channel         VARCHAR(16)  NOT NULL COMMENT '渠道',
    bill_type       VARCHAR(16)  NOT NULL COMMENT '账单记录类型：PAYMENT/REFUND',
    diff_type       VARCHAR(24)  NOT NULL COMMENT 'LOCAL_ONLY/CHANNEL_ONLY/AMOUNT_MISMATCH',
    bill_trade_no   VARCHAR(64)  DEFAULT NULL COMMENT '渠道账单交易号；LOCAL_ONLY 为空',
    local_no        VARCHAR(64)  DEFAULT NULL COMMENT '本地单号（pay_no 或 refund_id）；CHANNEL_ONLY 为空',
    local_amount    BIGINT       DEFAULT NULL COMMENT '本地金额，单位分',
    channel_amount  BIGINT       DEFAULT NULL COMMENT '账单金额，单位分',
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RESOLVED',
    remark          VARCHAR(255) DEFAULT NULL COMMENT '处理备注',
    resolved_by     VARCHAR(32)  DEFAULT NULL COMMENT '处理人',
    resolved_at     DATETIME     DEFAULT NULL COMMENT '处理时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (difference_id),
    KEY idx_batch_type (batch_id, diff_type),
    KEY idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道对账差异';