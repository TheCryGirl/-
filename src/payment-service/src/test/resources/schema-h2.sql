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
