-- 订单服务数据库表
-- 依据《05-接口契约》第五节与第十节。

CREATE TABLE IF NOT EXISTS `order` (
    order_id           VARCHAR(32)  NOT NULL COMMENT '订单ID',
    order_no           VARCHAR(32)  NOT NULL COMMENT '订单号，对外展示',
    buyer_id           VARCHAR(32)  NOT NULL COMMENT '买家ID',
    seller_id          VARCHAR(32)  NOT NULL COMMENT '卖家ID',
    status             VARCHAR(24)  NOT NULL COMMENT 'PENDING_PAYMENT/PAID/SHIPPED/COMPLETED/CANCELLED/REFUNDED',
    total_amount       BIGINT       NOT NULL COMMENT '商品总额，单位分',
    pay_amount         BIGINT       NOT NULL COMMENT '应付金额，单位分',
    receiver           VARCHAR(32)  NOT NULL COMMENT '收货人快照',
    receiver_phone     VARCHAR(11)  NOT NULL COMMENT '收货电话快照',
    address_full       VARCHAR(255) NOT NULL COMMENT '完整收货地址快照',
    tracking_no        VARCHAR(64)  DEFAULT NULL COMMENT '物流单号',
    expire_at          DATETIME     NOT NULL COMMENT '待支付过期时间',
    paid_at            DATETIME     DEFAULT NULL COMMENT '支付时间',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by         VARCHAR(32)  DEFAULT NULL COMMENT '创建人',
    updated_by         VARCHAR(32)  DEFAULT NULL COMMENT '更新人',
    is_deleted         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    inventory_released TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '库存是否已释放：0否 1是',
    PRIMARY KEY (order_id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_buyer_status (buyer_id, status, created_at),
    KEY idx_seller_status (seller_id, status, created_at),
    KEY idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

CREATE TABLE IF NOT EXISTS order_item (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '明细ID',
    order_id       VARCHAR(32)  NOT NULL COMMENT '订单ID',
    sku_id         VARCHAR(32)  NOT NULL COMMENT 'SKU ID',
    product_id     VARCHAR(32)  NOT NULL COMMENT '商品ID',
    product_title  VARCHAR(128) NOT NULL COMMENT '商品标题快照',
    spec_name      VARCHAR(64)  NOT NULL COMMENT '规格快照',
    price_snapshot BIGINT       NOT NULL COMMENT '下单时单价，单位分',
    quantity       INT          NOT NULL COMMENT '购买数量',
    subtotal       BIGINT       NOT NULL COMMENT '小计，单位分',
    main_image     VARCHAR(255) DEFAULT NULL COMMENT '主图快照',
    seller_id      VARCHAR(32)  NOT NULL COMMENT '卖家ID',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    is_deleted     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_sku (order_id, sku_id),
    KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细（价格快照）';

CREATE TABLE IF NOT EXISTS order_idempotency (
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    idem_key     VARCHAR(64) NOT NULL COMMENT '幂等键',
    buyer_id     VARCHAR(32) NOT NULL COMMENT '买家ID',
    request_hash VARCHAR(64) NOT NULL COMMENT '请求指纹',
    order_id     VARCHAR(32) NOT NULL COMMENT '订单ID',
    status       VARCHAR(16) NOT NULL COMMENT 'PROCESSING/SUCCESS',
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_idem_key (idem_key),
    KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='下单幂等记录';

CREATE TABLE IF NOT EXISTS order_compensation (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id      VARCHAR(32)  NOT NULL COMMENT '订单ID',
    status        VARCHAR(16)  NOT NULL COMMENT 'PENDING/DONE/DEAD',
    retry_count   INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    next_retry_at DATETIME     NOT NULL COMMENT '下次重试时间',
    last_error    VARCHAR(512) DEFAULT NULL COMMENT '最近错误',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_compensation_order (order_id),
    KEY idx_status_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存释放补偿任务';
CREATE TABLE IF NOT EXISTS config_audit (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    operator_id VARCHAR(32)  NOT NULL COMMENT '操作人 userId',
    data_id     VARCHAR(64)  NOT NULL COMMENT '配置 dataId',
    config_key  VARCHAR(128) NOT NULL COMMENT '配置键',
    old_value   VARCHAR(256) DEFAULT NULL COMMENT '修改前值',
    new_value   VARCHAR(256) NOT NULL COMMENT '修改后值',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (id),
    KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台配置变更留痕';

CREATE TABLE IF NOT EXISTS audit_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    audit_id      VARCHAR(32)  NOT NULL COMMENT '审计记录ID',
    operator_id   VARCHAR(32)  NOT NULL COMMENT '操作人 userId',
    operator_role VARCHAR(16)  DEFAULT NULL COMMENT '操作人角色',
    action        VARCHAR(32)  NOT NULL COMMENT '操作类型',
    target_type   VARCHAR(32)  DEFAULT NULL COMMENT '对象类型',
    target_id     VARCHAR(64)  DEFAULT NULL COMMENT '对象ID',
    detail        VARCHAR(1000) DEFAULT NULL COMMENT '操作说明',
    result        VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAIL',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_audit_id (audit_id),
    KEY idx_audit_operator (operator_id, created_at),
    KEY idx_audit_action (action, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台关键操作审计日志';