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
