-- 结算候选订单的跨库只读视图（#144 第二块）：生产上结算直接读 order-service 的
-- mall_order.order（组长口径），H2 里建同名 schema 与最小列集保证 SQL 两端一致。
CREATE SCHEMA IF NOT EXISTS mall_order;
DROP TABLE IF EXISTS mall_order.`order`;

CREATE TABLE mall_order.`order` (
    order_id     VARCHAR(32) NOT NULL,
    seller_id    VARCHAR(32) NOT NULL,
    pay_amount   BIGINT      NOT NULL,
    status       VARCHAR(24) NOT NULL,
    updated_at   DATETIME    DEFAULT NULL,
    is_deleted   TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (order_id)
);

DROP TABLE IF EXISTS settlement_item;
DROP TABLE IF EXISTS settlement;
DROP TABLE IF EXISTS commission_rule;

CREATE TABLE commission_rule (
    rule_id     VARCHAR(32) NOT NULL,
    scope       VARCHAR(16) NOT NULL,
    target_id   VARCHAR(32) NOT NULL,
    rate        INT         NOT NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(32) DEFAULT NULL,
    updated_by  VARCHAR(32) DEFAULT NULL,
    PRIMARY KEY (rule_id),
    CONSTRAINT uk_scope_target UNIQUE (scope, target_id)
);

CREATE TABLE settlement (
    settlement_id     VARCHAR(32) NOT NULL,
    seller_id         VARCHAR(32) NOT NULL,
    period_start      DATETIME    NOT NULL,
    period_end        DATETIME    NOT NULL,
    gross_amount      BIGINT      NOT NULL DEFAULT 0,
    commission_amount BIGINT      NOT NULL DEFAULT 0,
    net_amount        BIGINT      NOT NULL DEFAULT 0,
    status            VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(32) DEFAULT NULL,
    updated_by        VARCHAR(32) DEFAULT NULL,
    is_deleted        TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (settlement_id),
    CONSTRAINT uk_seller_period UNIQUE (seller_id, period_start)
);

CREATE TABLE settlement_item (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    settlement_id     VARCHAR(32) NOT NULL,
    order_id          VARCHAR(32) NOT NULL,
    gross_amount      BIGINT      NOT NULL,
    commission_amount BIGINT      NOT NULL,
    created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_item_order UNIQUE (order_id)
);

DROP TABLE IF EXISTS refund;
DROP TABLE IF EXISTS payment_callback;
DROP TABLE IF EXISTS payment;

CREATE TABLE payment (
    payment_id       VARCHAR(32)  NOT NULL,
    pay_no           VARCHAR(32)  NOT NULL,
    order_id         VARCHAR(32)  NOT NULL,
    buyer_id         VARCHAR(32)  NOT NULL,
    seller_id        VARCHAR(32)  DEFAULT NULL,
    channel          VARCHAR(16)  NOT NULL,
    amount           BIGINT       NOT NULL,
    refunded_amount  BIGINT       NOT NULL DEFAULT 0,
    status           VARCHAR(16)  NOT NULL,
    idempotency_key  VARCHAR(64)  DEFAULT NULL,
    paid_at          DATETIME     DEFAULT NULL,
    reconciled_at    DATETIME     DEFAULT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       VARCHAR(32)  DEFAULT NULL,
    updated_by       VARCHAR(32)  DEFAULT NULL,
    is_deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (payment_id),
    CONSTRAINT uk_pay_no UNIQUE (pay_no),
    CONSTRAINT uk_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_order_status UNIQUE (order_id, status)
);

CREATE TABLE payment_callback (
    callback_id     BIGINT       NOT NULL AUTO_INCREMENT,
    trade_no        VARCHAR(64)  NOT NULL,
    payment_id      VARCHAR(32)  NOT NULL,
    raw_payload     CLOB,
    sign_verified   TINYINT      NOT NULL DEFAULT 0,
    amount          BIGINT       NOT NULL,
    process_status  VARCHAR(16)  NOT NULL DEFAULT 'PROCESSING',
    error_message   VARCHAR(255) DEFAULT NULL,
    received_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (callback_id),
    CONSTRAINT uk_trade_no UNIQUE (trade_no)
);

CREATE TABLE refund (
    refund_id       VARCHAR(32)  NOT NULL,
    payment_id      VARCHAR(32)  NOT NULL,
    order_id        VARCHAR(32)  NOT NULL,
    buyer_id        VARCHAR(32)  NOT NULL,
    seller_id       VARCHAR(32)  DEFAULT NULL,
    amount          BIGINT       NOT NULL,
    reason          VARCHAR(255) DEFAULT NULL,
    status          VARCHAR(16)  NOT NULL,
    channel_refund_no VARCHAR(64) DEFAULT NULL,
    channel_error   VARCHAR(255) DEFAULT NULL,
    refunded_at     DATETIME     DEFAULT NULL,
    audited_by      VARCHAR(32)  DEFAULT NULL,
    audit_remark    VARCHAR(255) DEFAULT NULL,
    audited_at      DATETIME     DEFAULT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(32)  DEFAULT NULL,
    updated_by      VARCHAR(32)  DEFAULT NULL,
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (refund_id)
);

CREATE INDEX idx_refund_status_created ON refund (status, created_at);
CREATE INDEX idx_refund_buyer_status_created ON refund (buyer_id, status, created_at);
CREATE INDEX idx_refund_seller_status_created ON refund (seller_id, status, created_at);

DROP TABLE IF EXISTS after_sale_ticket;

CREATE TABLE after_sale_ticket (
    ticket_id             VARCHAR(32)  NOT NULL,
    order_id              VARCHAR(32)  NOT NULL,
    payment_id            VARCHAR(32)  NOT NULL,
    buyer_id              VARCHAR(32)  NOT NULL,
    seller_id             VARCHAR(32)  NOT NULL,
    after_sale_type       VARCHAR(24)  NOT NULL,
    amount                BIGINT       NOT NULL DEFAULT 0,
    reason                VARCHAR(255) DEFAULT NULL,
    reason_code           VARCHAR(24)  DEFAULT NULL,
    status                VARCHAR(24)  NOT NULL,
    refund_id             VARCHAR(32)  DEFAULT NULL,
    return_tracking_no    VARCHAR(64)  DEFAULT NULL,
    exchange_tracking_no  VARCHAR(64)  DEFAULT NULL,
    audit_remark          VARCHAR(255) DEFAULT NULL,
    audited_by            VARCHAR(32)  DEFAULT NULL,
    audited_at            DATETIME     DEFAULT NULL,
    buyer_shipped_at      DATETIME     DEFAULT NULL,
    seller_shipped_at     DATETIME     DEFAULT NULL,
    idempotency_key       VARCHAR(64)  NOT NULL,
    active_flag           TINYINT      DEFAULT 1,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by            VARCHAR(32)  DEFAULT NULL,
    updated_by            VARCHAR(32)  DEFAULT NULL,
    is_deleted            TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (ticket_id),
    CONSTRAINT uk_ticket_idem UNIQUE (idempotency_key),
    CONSTRAINT uk_order_active UNIQUE (order_id, active_flag)
);

CREATE INDEX idx_after_sale_buyer_created ON after_sale_ticket (buyer_id, created_at);
CREATE INDEX idx_after_sale_seller_status_created ON after_sale_ticket (seller_id, status, created_at);
CREATE INDEX idx_after_sale_status_created ON after_sale_ticket (status, created_at);

DROP TABLE IF EXISTS refund_notification_task;

CREATE TABLE refund_notification_task (
    event_id       BIGINT       NOT NULL AUTO_INCREMENT,
    order_id       VARCHAR(32)  NOT NULL,
    event_type     VARCHAR(32)  NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts       INT          NOT NULL DEFAULT 0,
    next_retry_at  DATETIME     DEFAULT NULL,
    lease_until    DATETIME     DEFAULT NULL,
    last_error     VARCHAR(255) DEFAULT NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id),
    CONSTRAINT uk_refund_notify_order_event UNIQUE (order_id, event_type)
);

CREATE INDEX idx_refund_notify_status_retry ON refund_notification_task (status, next_retry_at);

DROP TABLE IF EXISTS refund_callback;

CREATE TABLE refund_callback (
    callback_id     BIGINT       NOT NULL AUTO_INCREMENT,
    channel         VARCHAR(16)  NOT NULL,
    notify_id       VARCHAR(128) NOT NULL,
    refund_id       VARCHAR(32)  NOT NULL,
    raw_payload     CLOB,
    sign_verified   TINYINT      NOT NULL DEFAULT 0,
    amount          BIGINT       NOT NULL,
    process_status  VARCHAR(16)  NOT NULL DEFAULT 'PROCESSING',
    error_message   VARCHAR(255) DEFAULT NULL,
    received_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (callback_id),
    CONSTRAINT uk_channel_notify UNIQUE (channel, notify_id)
);

DROP TABLE IF EXISTS reconcile_difference;
DROP TABLE IF EXISTS reconcile_batch;

CREATE TABLE reconcile_batch (
    batch_id        VARCHAR(32)  NOT NULL,
    channel         VARCHAR(16)  NOT NULL,
    file_name       VARCHAR(255) DEFAULT NULL,
    total_rows      INT          NOT NULL DEFAULT 0,
    diff_count      INT          NOT NULL DEFAULT 0,
    imported_by     VARCHAR(32)  NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (batch_id)
);

CREATE TABLE reconcile_difference (
    difference_id   BIGINT       NOT NULL AUTO_INCREMENT,
    batch_id        VARCHAR(32)  NOT NULL,
    channel         VARCHAR(16)  NOT NULL,
    bill_type       VARCHAR(16)  NOT NULL,
    diff_type       VARCHAR(24)  NOT NULL,
    bill_trade_no   VARCHAR(64)  DEFAULT NULL,
    local_no        VARCHAR(64)  DEFAULT NULL,
    local_amount    BIGINT       DEFAULT NULL,
    channel_amount  BIGINT       DEFAULT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    remark          VARCHAR(255) DEFAULT NULL,
    resolved_by     VARCHAR(32)  DEFAULT NULL,
    resolved_at     DATETIME     DEFAULT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (difference_id)
);

CREATE INDEX idx_reconcile_diff_batch ON reconcile_difference (batch_id, diff_type);

DROP TABLE IF EXISTS after_sale_attachment;
DROP TABLE IF EXISTS after_sale_event;

CREATE TABLE after_sale_attachment (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_id       VARCHAR(32)  NOT NULL,
    url             VARCHAR(255) NOT NULL,
    uploaded_by     VARCHAR(32)  DEFAULT NULL,
    uploader_role   VARCHAR(16)  DEFAULT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_after_sale_attachment_ticket ON after_sale_attachment (ticket_id, created_at);

CREATE TABLE after_sale_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_id       VARCHAR(32)  NOT NULL,
    from_status     VARCHAR(24)  DEFAULT NULL,
    to_status       VARCHAR(24)  NOT NULL,
    operator_id     VARCHAR(32)  DEFAULT NULL,
    remark          VARCHAR(255) DEFAULT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_after_sale_event_ticket ON after_sale_event (ticket_id, created_at);

DROP TABLE IF EXISTS user_coupon;
DROP TABLE IF EXISTS coupon;
DROP TABLE IF EXISTS promotion;

CREATE TABLE coupon (
    coupon_id       VARCHAR(32)  NOT NULL,
    shop_id         VARCHAR(32)  NOT NULL,
    name            VARCHAR(64)  NOT NULL,
    type            VARCHAR(16)  NOT NULL,
    `value`         BIGINT       NOT NULL,
    min_amount      BIGINT       NOT NULL DEFAULT 0,
    total_qty       INT          NOT NULL,
    issued_qty      INT          NOT NULL DEFAULT 0,
    start_at        DATETIME     NOT NULL,
    end_at          DATETIME     NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(32)  DEFAULT NULL,
    updated_by      VARCHAR(32)  DEFAULT NULL,
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (coupon_id)
);

CREATE INDEX idx_coupon_shop_status ON coupon (shop_id, status);
CREATE INDEX idx_coupon_status_time ON coupon (status, start_at, end_at);

CREATE TABLE user_coupon (
    user_coupon_id  VARCHAR(32)  NOT NULL,
    coupon_id       VARCHAR(32)  NOT NULL,
    user_id         VARCHAR(32)  NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'UNUSED',
    order_id        VARCHAR(32)  DEFAULT NULL,
    received_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at         DATETIME     DEFAULT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (user_coupon_id),
    CONSTRAINT uk_user_coupon UNIQUE (user_id, coupon_id)
);

CREATE INDEX idx_user_coupon_user_status ON user_coupon (user_id, status);
CREATE INDEX idx_user_coupon_order ON user_coupon (order_id);

CREATE TABLE promotion (
    promotion_id    VARCHAR(32)  NOT NULL,
    scope           VARCHAR(16)  NOT NULL,
    shop_id         VARCHAR(32)  DEFAULT NULL,
    type            VARCHAR(16)  NOT NULL,
    threshold       BIGINT       NOT NULL,
    `value`         BIGINT       NOT NULL,
    stackable       TINYINT      NOT NULL DEFAULT 1,
    start_at        DATETIME     NOT NULL,
    end_at          DATETIME     NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(32)  DEFAULT NULL,
    updated_by      VARCHAR(32)  DEFAULT NULL,
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (promotion_id)
);

CREATE INDEX idx_promotion_status_time ON promotion (status, start_at, end_at);
