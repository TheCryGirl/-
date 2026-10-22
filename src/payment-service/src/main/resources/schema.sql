-- 支付服务数据库表
-- 依据《05-接口契约》第六节与第十节。

CREATE TABLE IF NOT EXISTS payment (
    payment_id       VARCHAR(32)  NOT NULL COMMENT '支付单ID',
    pay_no           VARCHAR(32)  NOT NULL COMMENT '支付单号',
    order_id         VARCHAR(32)  NOT NULL COMMENT '订单ID',
    buyer_id         VARCHAR(32)  NOT NULL COMMENT '买家ID',
    channel          VARCHAR(16)  NOT NULL COMMENT '支付渠道：ALIPAY/WECHAT/MOCK',
    amount           BIGINT       NOT NULL COMMENT '应付金额，单位分',
    refunded_amount  BIGINT       NOT NULL DEFAULT 0 COMMENT '累计已退金额，单位分',
    status           VARCHAR(16)  NOT NULL COMMENT '支付状态：PENDING/SUCCESS/FAILED/CLOSED/REFUNDED',
    idempotency_key  VARCHAR(64)  DEFAULT NULL COMMENT '创建支付单的幂等键',
    paid_at          DATETIME     DEFAULT NULL COMMENT '支付成功时间',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by       VARCHAR(32)  DEFAULT NULL COMMENT '创建人',
    updated_by       VARCHAR(32)  DEFAULT NULL COMMENT '更新人',
    is_deleted       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (payment_id),
    UNIQUE KEY uk_pay_no (pay_no),
    UNIQUE KEY uk_idempotency_key (idempotency_key),
    -- 同一订单同一状态只允许一条，保证并发下至多一个待支付单（BR-21）
    UNIQUE KEY uk_order_status (order_id, status),
    KEY idx_buyer_created (buyer_id, created_at),
    KEY idx_order_created (order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付单';

CREATE TABLE IF NOT EXISTS payment_callback (
    callback_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '回调流水ID',
    trade_no        VARCHAR(64)  NOT NULL COMMENT '第三方交易号',
    payment_id      VARCHAR(32)  NOT NULL COMMENT '支付单ID',
    raw_payload     TEXT         COMMENT '回调原始报文',
    sign_verified   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '验签结果：0失败 1通过',
    amount          BIGINT       NOT NULL COMMENT '回调金额，单位分',
    process_status  VARCHAR(16)  NOT NULL DEFAULT 'PROCESSING' COMMENT '处理状态：PROCESSING/PROCESSED/DUPLICATE/AMOUNT_MISMATCH',
    error_message   VARCHAR(255) DEFAULT NULL COMMENT '处理失败原因',
    received_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
    PRIMARY KEY (callback_id),
    -- 去重的唯一保障，不能改成先查再插
    UNIQUE KEY uk_trade_no (trade_no),
    KEY idx_payment_id (payment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付回调流水';

CREATE TABLE IF NOT EXISTS refund (
    refund_id       VARCHAR(32)  NOT NULL COMMENT '退款单ID',
    payment_id      VARCHAR(32)  NOT NULL COMMENT '支付单ID',
    order_id        VARCHAR(32)  NOT NULL COMMENT '订单ID',
    buyer_id        VARCHAR(32)  NOT NULL COMMENT '买家ID',
    amount          BIGINT       NOT NULL COMMENT '退款金额，单位分',
    reason          VARCHAR(255) DEFAULT NULL COMMENT '退款原因',
    status          VARCHAR(16)  NOT NULL COMMENT '退款状态：PENDING/REJECTED/SUCCESS/FAILED',
    audited_by      VARCHAR(32)  DEFAULT NULL COMMENT '审核人',
    audit_remark    VARCHAR(255) DEFAULT NULL COMMENT '审核备注',
    audited_at      DATETIME     DEFAULT NULL COMMENT '审核时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by      VARCHAR(32)  DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(32)  DEFAULT NULL COMMENT '更新人',
    is_deleted      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (refund_id),
    KEY idx_payment_created (payment_id, created_at),
    KEY idx_status_created (status, created_at),
    KEY idx_buyer_status_created (buyer_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单';
