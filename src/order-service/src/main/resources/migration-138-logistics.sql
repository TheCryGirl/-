-- ============================================================
-- 老库升级脚本：为已有的 mall_order 补上 #138 物流履约所需的列与新表
--
-- 背景（Issue #138）：
--   schema.sql 用的是 CREATE TABLE IF NOT EXISTS，
--   对**已存在**的 `order` 表不会自动加新列（express_company / shipped_at），
--   因此老库需要单独执行本脚本。新库直接跑 schema.sql 即可，无需执行本脚本。
--
-- 用法：
--   mysql -u root -p mall_order < src/order-service/src/main/resources/migration-138-logistics.sql
--
-- 幂等性：
--   MySQL 8 不支持 ALTER TABLE ... ADD COLUMN IF NOT EXISTS（MariaDB 才有），
--   所以用 information_schema 判断后动态执行，重复运行不会报错。
-- ============================================================

DROP PROCEDURE IF EXISTS migrate_order_logistics;

DELIMITER $$

CREATE PROCEDURE migrate_order_logistics()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'order'
          AND COLUMN_NAME  = 'express_company'
    ) THEN
        ALTER TABLE `order`
            ADD COLUMN express_company VARCHAR(16) NULL
            COMMENT '快递公司编码，见 ExpressCompany 枚举';
        SELECT '已为 order 表添加 express_company 列' AS result;
    ELSE
        SELECT 'order 表已存在 express_company 列，跳过' AS result;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'order'
          AND COLUMN_NAME  = 'shipped_at'
    ) THEN
        ALTER TABLE `order`
            ADD COLUMN shipped_at DATETIME NULL
            COMMENT '发货时间，自动确认收货的起算点';
        SELECT '已为 order 表添加 shipped_at 列' AS result;
    ELSE
        SELECT 'order 表已存在 shipped_at 列，跳过' AS result;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'order'
          AND INDEX_NAME   = 'idx_status_shipped'
    ) THEN
        ALTER TABLE `order` ADD KEY idx_status_shipped (status, shipped_at);
        SELECT '已为 order 表添加 idx_status_shipped 索引' AS result;
    ELSE
        SELECT 'order 表已存在 idx_status_shipped 索引，跳过' AS result;
    END IF;
END$$

DELIMITER ;

CALL migrate_order_logistics();
DROP PROCEDURE migrate_order_logistics;

-- 轨迹表用 CREATE TABLE IF NOT EXISTS，可重复执行
CREATE TABLE IF NOT EXISTS order_logistics_track (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    track_id    VARCHAR(32)   NOT NULL COMMENT '轨迹ID',
    order_id    VARCHAR(32)   NOT NULL COMMENT '订单ID',
    status      VARCHAR(24)   NOT NULL COMMENT 'COLLECTED/IN_TRANSIT/OUT_FOR_DELIVERY/SIGNED/EXCEPTION',
    description VARCHAR(255)  NOT NULL COMMENT '轨迹描述文本',
    occurred_at DATETIME      NOT NULL COMMENT '发生时间，时间线按此正序排列',
    raw_json    VARCHAR(2048) DEFAULT NULL COMMENT '录入时的原始报文，供以后对接外部接口对账',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '录入时间',
    created_by  VARCHAR(32)   DEFAULT NULL COMMENT '录入人（卖家/管理员）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_track_id (track_id),
    KEY idx_track_order_time (order_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流轨迹（任务 #138，手工录入）';

-- 存量已发货订单的 shipped_at 补值：
-- 这些单在加列之前就已 SHIPPED，没有发货时间，自动确认收货无从起算。
-- 按 updated_at 兜底（最后一次改状态的时间最接近真实发货时间），只处理仍为空的行。
UPDATE `order`
SET shipped_at = updated_at
WHERE status = 'SHIPPED'
  AND shipped_at IS NULL
  AND is_deleted = 0;

SELECT '已按 updated_at 回填存量 SHIPPED 订单的 shipped_at' AS result;
