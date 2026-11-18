-- ============================================================
-- 老库升级脚本：为已有的 mall_user.shop 补入驻审核字段，并新建审核流水表
--
-- 背景（Issue #144）：
--   schema.sql 用的是 CREATE TABLE IF NOT EXISTS，
--   对**已存在**的 shop 表不会自动加新列，因此老库需要单独执行本脚本。
--
-- 用法：
--   mysql -u root -p mall_user < src/user-service/src/main/resources/migration-144-shop-apply.sql
--
-- 幂等性：
--   MySQL 8 不支持 ALTER TABLE ... ADD COLUMN IF NOT EXISTS，
--   所以用 information_schema 判断后动态执行，重复运行不会报错。
--
-- 历史数据处理：
--   apply_status 默认 APPROVED、deposit_amount 默认 0，
--   存量店铺直接视为「已过审、保证金未缴纳（仅作记录）」，不会被升级脚本挡住发布商品。
-- ============================================================

DROP PROCEDURE IF EXISTS migrate_shop_apply;

DELIMITER $$

CREATE PROCEDURE migrate_shop_apply()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop' AND COLUMN_NAME = 'apply_status'
    ) THEN
        ALTER TABLE shop
            ADD COLUMN apply_status VARCHAR(16) NOT NULL DEFAULT 'APPROVED'
            COMMENT '入驻审核状态：PENDING待审核/APPROVED已通过/REJECTED已驳回；历史店铺默认已通过';
        SELECT '已为 shop 表添加 apply_status 列' AS result;
    ELSE
        SELECT 'shop 表已存在 apply_status 列，跳过' AS result;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop' AND COLUMN_NAME = 'deposit_amount'
    ) THEN
        ALTER TABLE shop
            ADD COLUMN deposit_amount BIGINT NOT NULL DEFAULT 0
            COMMENT '保证金金额（分），0 表示尚未缴纳；仅作记录不拦截发布';
        SELECT '已为 shop 表添加 deposit_amount 列' AS result;
    ELSE
        SELECT 'shop 表已存在 deposit_amount 列，跳过' AS result;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop' AND COLUMN_NAME = 'qualification_json'
    ) THEN
        ALTER TABLE shop
            ADD COLUMN qualification_json VARCHAR(2000) DEFAULT NULL
            COMMENT '入驻资质快照 JSON：主体类型、证照编号、资质材料地址';
        SELECT '已为 shop 表添加 qualification_json 列' AS result;
    ELSE
        SELECT 'shop 表已存在 qualification_json 列，跳过' AS result;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop' AND INDEX_NAME = 'idx_shop_apply_status'
    ) THEN
        ALTER TABLE shop ADD INDEX idx_shop_apply_status (apply_status);
        SELECT '已为 shop 表添加 apply_status 索引' AS result;
    ELSE
        SELECT 'shop 表已存在 apply_status 索引，跳过' AS result;
    END IF;
END$$

DELIMITER ;

CALL migrate_shop_apply();
DROP PROCEDURE migrate_shop_apply;

CREATE TABLE IF NOT EXISTS shop_apply_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '流水ID',
    shop_id     VARCHAR(32)  NOT NULL COMMENT '店铺ID',
    from_status VARCHAR(16)  DEFAULT NULL COMMENT '变更前审核状态，首次申请为 NULL',
    to_status   VARCHAR(16)  NOT NULL COMMENT '变更后审核状态',
    operator_id VARCHAR(32)  DEFAULT NULL COMMENT '操作人用户ID：卖家申请或管理员审核',
    remark      VARCHAR(255) DEFAULT NULL COMMENT '驳回理由或审核备注',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_apply_log_shop (shop_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺入驻审核流水（#144）';
