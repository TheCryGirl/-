-- 订单服务数据库表
-- 依据《05-接口契约》第五节与第十节。

-- 主订单（#135 多店铺合并结算）：跨店一次支付时聚合各店铺子订单。
-- 历史单店订单不产生主订单，order.main_order_id 为 null 时按单店兼容。
CREATE TABLE IF NOT EXISTS main_order (
    main_order_id   VARCHAR(32)  NOT NULL COMMENT '主订单ID',
    main_order_no   VARCHAR(32)  NOT NULL COMMENT '主订单号，对外展示',
    buyer_id        VARCHAR(32)  NOT NULL COMMENT '买家ID',
    status          VARCHAR(24)  NOT NULL COMMENT 'PENDING_PAYMENT/PAID/CANCELLED',
    pay_amount      BIGINT       NOT NULL COMMENT '应付合计，单位分，等于各子订单pay_amount之和',
    paid_at         DATETIME     DEFAULT NULL COMMENT '支付时间（全部子订单支付完成）',
    expire_at       DATETIME     NOT NULL COMMENT '待支付过期时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by      VARCHAR(32)  DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(32)  DEFAULT NULL COMMENT '更新人',
    is_deleted      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (main_order_id),
    UNIQUE KEY uk_main_order_no (main_order_no),
    KEY idx_main_buyer_status (buyer_id, status, created_at),
    KEY idx_main_status_expire (status, expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主订单（跨店合并结算）';

CREATE TABLE IF NOT EXISTS `order` (
    order_id           VARCHAR(32)  NOT NULL COMMENT '订单ID',
    order_no           VARCHAR(32)  NOT NULL COMMENT '订单号，对外展示',
    buyer_id           VARCHAR(32)  NOT NULL COMMENT '买家ID',
    seller_id          VARCHAR(32)  NOT NULL COMMENT '卖家ID',
    main_order_id      VARCHAR(32)  DEFAULT NULL COMMENT '主订单ID（#135）；历史单店订单为null',
    status             VARCHAR(24)  NOT NULL COMMENT 'PENDING_PAYMENT/PAID/SHIPPED/COMPLETED/CANCELLED/REFUNDED',
    total_amount       BIGINT       NOT NULL COMMENT '商品总额，单位分',
    freight_amount     BIGINT       NOT NULL DEFAULT 0 COMMENT '运费，单位分（#136）',
    discount_amount    BIGINT       NOT NULL DEFAULT 0 COMMENT '优惠抵扣，单位分（#136）',
    pay_amount         BIGINT       NOT NULL COMMENT '应付金额 = 商品总额 + 运费 - 优惠，单位分',
    receiver           VARCHAR(32)  NOT NULL COMMENT '收货人快照',
    receiver_phone     VARCHAR(11)  NOT NULL COMMENT '收货电话快照',
    address_full       VARCHAR(255) NOT NULL COMMENT '完整收货地址快照',
    tracking_no        VARCHAR(64)  DEFAULT NULL COMMENT '物流单号',
    express_company    VARCHAR(16)  DEFAULT NULL COMMENT '快递公司编码，见 ExpressCompany 枚举',
    shipped_at         DATETIME     DEFAULT NULL COMMENT '发货时间，自动确认收货的起算点',
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
    KEY idx_status_created (status, created_at),
    KEY idx_main_order_id (main_order_id),
    KEY idx_status_shipped (status, shipped_at)
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

CREATE TABLE IF NOT EXISTS order_evaluation (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    evaluation_id VARCHAR(32)  NOT NULL COMMENT '评价ID',
    order_id      VARCHAR(32)  NOT NULL COMMENT '订单ID，一单一评',
    buyer_id      VARCHAR(32)  NOT NULL COMMENT '买家ID',
    seller_id     VARCHAR(32)  NOT NULL COMMENT '卖家ID',
    rating        TINYINT      NOT NULL COMMENT '评分 1~5 星',
    content       VARCHAR(500) DEFAULT NULL COMMENT '评价文字',
    images        VARCHAR(1024) DEFAULT NULL COMMENT '晒图地址，逗号分隔，最多3张',
    anonymous     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否匿名：0否 1是',
    status        VARCHAR(16)  NOT NULL DEFAULT 'VISIBLE' COMMENT 'VISIBLE/HIDDEN',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_evaluation_id (evaluation_id),
    UNIQUE KEY uk_evaluation_order (order_id),
    KEY idx_evaluation_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单评价';

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

CREATE TABLE IF NOT EXISTS order_logistics_track (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    track_id    VARCHAR(32)  NOT NULL COMMENT '轨迹ID',
    order_id    VARCHAR(32)  NOT NULL COMMENT '订单ID',
    status      VARCHAR(24)  NOT NULL COMMENT 'COLLECTED/IN_TRANSIT/OUT_FOR_DELIVERY/SIGNED/EXCEPTION',
    description VARCHAR(255) NOT NULL COMMENT '轨迹描述文本',
    occurred_at DATETIME     NOT NULL COMMENT '发生时间，时间线按此正序排列',
    raw_json    VARCHAR(2048) DEFAULT NULL COMMENT '录入时的原始报文，供以后对接外部接口对账',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '录入时间',
    created_by  VARCHAR(32)  DEFAULT NULL COMMENT '录入人（卖家/管理员）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_track_id (track_id),
    KEY idx_track_order_time (order_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流轨迹（任务 #138，手工录入）';