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
