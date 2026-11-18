-- ============================================================
-- 老库升级脚本（#140）：促销工具（优惠券 / 满减满折活动）
--   新增 coupon / user_coupon / promotion 三张表，不改动既有表。
--
-- 用法：
--   mysql -u root -p mall_payment < src/payment-service/src/main/resources/migration-140-promotion.sql
--
-- 幂等性：全部为 CREATE TABLE IF NOT EXISTS，可重复执行。
-- ============================================================

CREATE TABLE IF NOT EXISTS coupon (
    coupon_id       VARCHAR(32)  NOT NULL COMMENT '券模板ID',
    shop_id         VARCHAR(32)  NOT NULL COMMENT '归属店铺ID；平台券为 PLATFORM',
    name            VARCHAR(64)  NOT NULL COMMENT '券名称',
    type            VARCHAR(16)  NOT NULL COMMENT '券类型：FIXED 满减券 / PERCENT 折扣券',
    `value`         BIGINT       NOT NULL COMMENT '券值：FIXED=面额(分)；PERCENT=折扣百分比整数(85=8.5折)',
    min_amount      BIGINT       NOT NULL DEFAULT 0 COMMENT '使用门槛金额，单位分',
    total_qty       INT          NOT NULL COMMENT '发行总量',
    issued_qty      INT          NOT NULL DEFAULT 0 COMMENT '已领取数量',
    start_at        DATETIME     NOT NULL COMMENT '可领取开始时间',
    end_at          DATETIME     NOT NULL COMMENT '可领取/使用截止时间',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by      VARCHAR(32)  DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(32)  DEFAULT NULL COMMENT '更新人',
    is_deleted      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (coupon_id),
    KEY idx_shop_status (shop_id, status),
    KEY idx_status_time (status, start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板';

CREATE TABLE IF NOT EXISTS user_coupon (
    user_coupon_id  VARCHAR(32)  NOT NULL COMMENT '用户券ID',
    coupon_id       VARCHAR(32)  NOT NULL COMMENT '券模板ID',
    user_id         VARCHAR(32)  NOT NULL COMMENT '用户ID',
    status          VARCHAR(16)  NOT NULL DEFAULT 'UNUSED' COMMENT '状态：UNUSED/USED/EXPIRED',
    order_id        VARCHAR(32)  DEFAULT NULL COMMENT '占用订单ID；释放后清空',
    received_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
    used_at         DATETIME     DEFAULT NULL COMMENT '使用时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (user_coupon_id),
    UNIQUE KEY uk_user_coupon (user_id, coupon_id),
    KEY idx_user_status (user_id, status),
    KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券';

CREATE TABLE IF NOT EXISTS promotion (
    promotion_id    VARCHAR(32)  NOT NULL COMMENT '活动ID',
    scope           VARCHAR(16)  NOT NULL COMMENT '范围：PLATFORM 平台 / SHOP 店铺',
    shop_id         VARCHAR(32)  DEFAULT NULL COMMENT '店铺ID；平台活动为 NULL',
    type            VARCHAR(16)  NOT NULL COMMENT '活动类型：FULL_REDUCE 满减 / FULL_DISCOUNT 满折',
    threshold       BIGINT       NOT NULL COMMENT '门槛金额，单位分',
    `value`         BIGINT       NOT NULL COMMENT '优惠值：满减=减免分；满折=折扣百分比整数(85=8.5折)',
    stackable       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '可否与优惠券叠加：1可 0不可',
    start_at        DATETIME     NOT NULL COMMENT '生效开始时间',
    end_at          DATETIME     NOT NULL COMMENT '生效截止时间',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by      VARCHAR(32)  DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(32)  DEFAULT NULL COMMENT '更新人',
    is_deleted      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (promotion_id),
    KEY idx_status_time (status, start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='满减/满折活动';
