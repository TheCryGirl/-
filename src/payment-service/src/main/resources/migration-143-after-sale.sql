-- ============================================================
-- 老库升级脚本（#143）：售后举证与平台仲裁
--   after_sale_ticket 加 reason_code；新增 after_sale_attachment /
--   after_sale_event 两张表
--
-- 用法：
--   mysql -u root -p mall_payment < src/payment-service/src/main/resources/migration-143-after-sale.sql
--
-- 幂等性：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，
-- 用 information_schema 判断后动态执行；新表用 CREATE TABLE IF NOT EXISTS。
-- ============================================================

DROP PROCEDURE IF EXISTS migrate_143_after_sale;

DELIMITER $$

CREATE PROCEDURE migrate_143_after_sale()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'after_sale_ticket'
          AND COLUMN_NAME = 'reason_code'
    ) THEN
        ALTER TABLE after_sale_ticket
            ADD COLUMN reason_code VARCHAR(24) DEFAULT NULL COMMENT '售后原因枚举（#143）：QUALITY/NOT_AS_DESCRIBED/DAMAGED/WRONG_ITEM/NO_LONGER_NEEDED/OTHER' AFTER reason;
    END IF;
END$$

DELIMITER ;

CALL migrate_143_after_sale();
DROP PROCEDURE migrate_143_after_sale;

CREATE TABLE IF NOT EXISTS after_sale_attachment (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '附件ID',
    ticket_id       VARCHAR(32)  NOT NULL COMMENT '售后单ID',
    url             VARCHAR(255) NOT NULL COMMENT '站内相对路径 /after-sale/{uuid}.{ext}',
    uploaded_by     VARCHAR(32)  DEFAULT NULL COMMENT '上传人',
    uploader_role   VARCHAR(16)  DEFAULT NULL COMMENT '上传人角色：BUYER/SELLER/ADMIN',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    PRIMARY KEY (id),
    KEY idx_ticket_created (ticket_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后凭证附件';

CREATE TABLE IF NOT EXISTS after_sale_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '事件ID',
    ticket_id       VARCHAR(32)  NOT NULL COMMENT '售后单ID',
    from_status     VARCHAR(24)  DEFAULT NULL COMMENT '变更前状态；创建时为 NULL',
    to_status       VARCHAR(24)  NOT NULL COMMENT '变更后状态',
    operator_id     VARCHAR(32)  DEFAULT NULL COMMENT '操作人；系统回调为 SYSTEM',
    remark          VARCHAR(255) DEFAULT NULL COMMENT '操作备注',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
    PRIMARY KEY (id),
    KEY idx_ticket_created (ticket_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后进度时间线';
