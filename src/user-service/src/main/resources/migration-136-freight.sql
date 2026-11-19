-- #136 运费模板与优惠计价：user-service 侧增量脚本
-- 说明：schema.sql 用 CREATE TABLE IF NOT EXISTS 覆盖新库；既有库请手工执行本脚本一次。
-- 可重复执行；列已存在也会继续创建缺少的表。

SET @mall_136_shop_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
           AND TABLE_NAME='shop' AND COLUMN_NAME='freight_template_id'),
    'SELECT 1',
    'ALTER TABLE shop ADD COLUMN freight_template_id VARCHAR(32) DEFAULT NULL COMMENT ''当前生效的运费模板ID（#136）'''
);
PREPARE mall_136_shop_stmt FROM @mall_136_shop_ddl;
EXECUTE mall_136_shop_stmt;
DEALLOCATE PREPARE mall_136_shop_stmt;

CREATE TABLE IF NOT EXISTS freight_template (
    template_id    VARCHAR(32)  NOT NULL COMMENT '运费模板ID',
    shop_id        VARCHAR(32)  NOT NULL COMMENT '所属店铺ID',
    name           VARCHAR(64)  NOT NULL COMMENT '模板名称，如「全国包邮」',
    free_threshold BIGINT       NOT NULL DEFAULT 0 COMMENT '包邮门槛，单位分；0 表示不包邮',
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE启用/DISABLED停用',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by     VARCHAR(32)  DEFAULT NULL COMMENT '创建人',
    updated_by     VARCHAR(32)  DEFAULT NULL COMMENT '更新人',
    is_deleted     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (template_id),
    UNIQUE KEY uk_freight_template_shop (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运费模板';

CREATE TABLE IF NOT EXISTS freight_rule (
    rule_id     VARCHAR(32) NOT NULL COMMENT '规则ID',
    template_id VARCHAR(32) NOT NULL COMMENT '所属模板ID',
    region_code VARCHAR(32) NOT NULL COMMENT '收货地区：省级名称或 DEFAULT',
    min_amount  BIGINT      NOT NULL DEFAULT 0 COMMENT '适用该规则的最低商品金额，单位分',
    fee         BIGINT      NOT NULL DEFAULT 0 COMMENT '运费，单位分',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted  TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (rule_id),
    UNIQUE KEY uk_freight_rule (template_id, region_code, min_amount),
    KEY idx_freight_rule_template (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '分地区运费规则';
