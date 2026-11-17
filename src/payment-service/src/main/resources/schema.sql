-- 支付服务数据库表
-- 依据《05-接口契约》第六节与第十节。

CREATE TABLE IF NOT EXISTS payment (
    payment_id       VARCHAR(32)  NOT NULL COMMENT '支付单ID',
    pay_no           VARCHAR(32)  NOT NULL COMMENT '支付单号',
    order_id         VARCHAR(32)  NOT NULL COMMENT '订单ID',
    buyer_id         VARCHAR(32)  NOT NULL COMMENT '买家ID',
    seller_id        VARCHAR(32)  DEFAULT NULL COMMENT '卖家ID快照',
    channel          VARCHAR(16)  NOT NULL COMMENT '支付渠道：ALIPAY/WECHAT/MOCK',
    amount           BIGINT       NOT NULL COMMENT '应付金额，单位分',
    refunded_amount  BIGINT       NOT NULL DEFAULT 0 COMMENT '累计已退金额，单位分',
    status           VARCHAR(16)  NOT NULL COMMENT '支付状态：PENDING/SUCCESS/FAILED/CLOSED/REFUNDED',
    idempotency_key  VARCHAR(64)  DEFAULT NULL COMMENT '创建支付单的幂等键',
    paid_at          DATETIME     DEFAULT NULL COMMENT '支付成功时间',
    reconciled_at    DATETIME     DEFAULT NULL COMMENT '支付后对账完成时间；为空表示待对账',
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
    KEY idx_order_created (order_id, created_at),
    KEY idx_seller_created (seller_id, created_at)
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
    seller_id       VARCHAR(32)  DEFAULT NULL COMMENT '卖家ID快照',
    amount          BIGINT       NOT NULL COMMENT '退款金额，单位分',
    reason          VARCHAR(255) DEFAULT NULL COMMENT '退款原因',
    status          VARCHAR(16)  NOT NULL COMMENT '退款状态：PENDING/PROCESSING/REJECTED/SUCCESS/FAILED',
    channel_refund_no VARCHAR(64) DEFAULT NULL COMMENT '渠道退款单号（#137 原路退款）',
    channel_error   VARCHAR(255) DEFAULT NULL COMMENT '渠道退款失败原因（不含凭据）',
    refunded_at     DATETIME     DEFAULT NULL COMMENT '渠道退款到账时间',
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
    KEY idx_buyer_status_created (buyer_id, status, created_at),
    KEY idx_seller_status_created (seller_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单';

CREATE TABLE IF NOT EXISTS after_sale_ticket (
    ticket_id             VARCHAR(32)  NOT NULL COMMENT '售后单ID',
    order_id              VARCHAR(32)  NOT NULL COMMENT '订单ID',
    payment_id            VARCHAR(32)  NOT NULL COMMENT '支付单ID',
    buyer_id              VARCHAR(32)  NOT NULL COMMENT '买家ID',
    seller_id             VARCHAR(32)  NOT NULL COMMENT '卖家ID',
    after_sale_type       VARCHAR(24)  NOT NULL COMMENT '售后类型：REFUND_ONLY/RETURN_REFUND/EXCHANGE',
    amount                BIGINT       NOT NULL DEFAULT 0 COMMENT '申请退款金额，换货为0',
    reason                VARCHAR(255) DEFAULT NULL COMMENT '售后原因',
    reason_code           VARCHAR(24)  DEFAULT NULL COMMENT '售后原因枚举（#143）：QUALITY/NOT_AS_DESCRIBED/DAMAGED/WRONG_ITEM/NO_LONGER_NEEDED/OTHER',
    status                VARCHAR(24)  NOT NULL COMMENT 'PENDING/APPROVED/BUYER_SHIPPED/SELLER_RECEIVED/REFUNDED/COMPLETED/REJECTED/CANCELLED/ARBITRATING',
    refund_id             VARCHAR(32)  DEFAULT NULL COMMENT '退款单ID（退款类型执行后写入）',
    return_tracking_no    VARCHAR(64)  DEFAULT NULL COMMENT '买家退货物流单号',
    exchange_tracking_no  VARCHAR(64)  DEFAULT NULL COMMENT '卖家换货重发物流单号',
    audit_remark          VARCHAR(255) DEFAULT NULL COMMENT '最新审核/处理备注',
    audited_by            VARCHAR(32)  DEFAULT NULL COMMENT '审核人',
    audited_at            DATETIME     DEFAULT NULL COMMENT '审核时间',
    buyer_shipped_at      DATETIME     DEFAULT NULL COMMENT '买家退货时间',
    seller_shipped_at     DATETIME     DEFAULT NULL COMMENT '卖家换货重发时间',
    idempotency_key       VARCHAR(64)  NOT NULL COMMENT '创建幂等键',
    active_flag           TINYINT(1)   DEFAULT 1 COMMENT '进行中标记：1进行中，NULL已结束',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by            VARCHAR(32)  DEFAULT NULL COMMENT '创建人',
    updated_by            VARCHAR(32)  DEFAULT NULL COMMENT '更新人',
    is_deleted            TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (ticket_id),
    UNIQUE KEY uk_ticket_idem (idempotency_key),
    -- 同一订单同时只允许一条进行中的售后单；结束后 active_flag 置 NULL
    UNIQUE KEY uk_order_active (order_id, active_flag),
    KEY idx_buyer_created (buyer_id, created_at),
    KEY idx_seller_status_created (seller_id, status, created_at),
    KEY idx_status_created (status, created_at),
    KEY idx_payment_id (payment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后单';

-- 退款后的外部通知任务（order-service 退款完成、product-service 回补库存）。
-- 与退款审核在同一个本地事务里落库，事务提交后立即投递；失败的任务由定时扫描
-- 通过条件 UPDATE 原子抢占（status + 过期租约）后独立重试，不会静默丢弃。
CREATE TABLE IF NOT EXISTS refund_notification_task (
    event_id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    order_id       VARCHAR(32)  NOT NULL COMMENT '订单ID',
    event_type     VARCHAR(32)  NOT NULL COMMENT '事件类型：ORDER_REFUNDED/STOCK_RESTOCK',
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/PROCESSING/DONE',
    attempts       INT          NOT NULL DEFAULT 0 COMMENT '已尝试次数（含失败）',
    next_retry_at  DATETIME     DEFAULT NULL COMMENT '下次可重试时间，为空表示立即可投递',
    lease_until    DATETIME     DEFAULT NULL COMMENT '处理中租约到期时间，过期可被其他实例回收',
    last_error     VARCHAR(255) DEFAULT NULL COMMENT '最近一次失败原因（不含凭据与堆栈）',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (event_id),
    -- 同一订单的同一事件只允许一条任务，重复审核也不会重复通知
    UNIQUE KEY uk_order_event (order_id, event_type),
    KEY idx_status_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款通知任务';

-- 退款异步回调流水（#137）。去重靠 (channel, notify_id) 唯一约束，不使用先查再插。
CREATE TABLE IF NOT EXISTS refund_callback (
    callback_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '回调流水ID',
    channel         VARCHAR(16)  NOT NULL COMMENT '渠道：ALIPAY/MOCK',
    notify_id       VARCHAR(128) NOT NULL COMMENT '渠道通知唯一标识',
    refund_id       VARCHAR(32)  NOT NULL COMMENT '退款单ID',
    raw_payload     TEXT         COMMENT '回调原始参数（JSON）',
    sign_verified   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '验签结果：0失败 1通过',
    amount          BIGINT       NOT NULL COMMENT '回调退款金额，单位分',
    process_status  VARCHAR(16)  NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/PROCESSED/DUPLICATE/AMOUNT_MISMATCH',
    error_message   VARCHAR(255) DEFAULT NULL COMMENT '处理失败原因',
    received_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
    PRIMARY KEY (callback_id),
    UNIQUE KEY uk_channel_notify (channel, notify_id),
    KEY idx_refund_id (refund_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款回调流水';

-- 渠道对账批次（#137）。一次 CSV 导入一个批次，只出差异清单，不修改 payment/refund。
CREATE TABLE IF NOT EXISTS reconcile_batch (
    batch_id        VARCHAR(32)  NOT NULL COMMENT '批次ID',
    channel         VARCHAR(16)  NOT NULL COMMENT '对账渠道',
    file_name       VARCHAR(255) DEFAULT NULL COMMENT '账单文件名',
    total_rows      INT          NOT NULL DEFAULT 0 COMMENT '账单有效行数',
    diff_count      INT          NOT NULL DEFAULT 0 COMMENT '差异条数',
    imported_by     VARCHAR(32)  NOT NULL COMMENT '导入人（管理员）',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '导入时间',
    PRIMARY KEY (batch_id),
    KEY idx_channel_created (channel, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道对账批次';

-- 渠道对账差异（#137）。三类：LOCAL_ONLY / CHANNEL_ONLY / AMOUNT_MISMATCH。
CREATE TABLE IF NOT EXISTS reconcile_difference (
    difference_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '差异ID',
    batch_id        VARCHAR(32)  NOT NULL COMMENT '批次ID',
    channel         VARCHAR(16)  NOT NULL COMMENT '渠道',
    bill_type       VARCHAR(16)  NOT NULL COMMENT '账单记录类型：PAYMENT/REFUND',
    diff_type       VARCHAR(24)  NOT NULL COMMENT 'LOCAL_ONLY/CHANNEL_ONLY/AMOUNT_MISMATCH',
    bill_trade_no   VARCHAR(64)  DEFAULT NULL COMMENT '渠道账单交易号；LOCAL_ONLY 为空',
    local_no        VARCHAR(64)  DEFAULT NULL COMMENT '本地单号（pay_no 或 refund_id）；CHANNEL_ONLY 为空',
    local_amount    BIGINT       DEFAULT NULL COMMENT '本地金额，单位分',
    channel_amount  BIGINT       DEFAULT NULL COMMENT '账单金额，单位分',
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RESOLVED',
    remark          VARCHAR(255) DEFAULT NULL COMMENT '处理备注',
    resolved_by     VARCHAR(32)  DEFAULT NULL COMMENT '处理人',
    resolved_at     DATETIME     DEFAULT NULL COMMENT '处理时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (difference_id),
    KEY idx_batch_type (batch_id, diff_type),
    KEY idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道对账差异';

-- 售后凭证附件（#143）。先上传后引用：文件落盘 uploads/after-sale/，
-- 创建/补充售后单时绑定站内路径；单次提交最多 3 张（服务层校验）。
CREATE TABLE IF NOT EXISTS after_sale_attachment (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '附件ID',
    ticket_id       VARCHAR(32)  NOT NULL COMMENT '售后单ID',
    url             VARCHAR(255) NOT NULL COMMENT '站内相对路径 /after-sale/{uuid}.{ext}',
    uploaded_by     VARCHAR(32)  DEFAULT NULL COMMENT '上传人',
    uploader_role   VARCHAR(16)  DEFAULT NULL COMMENT '上传人角色：BUYER/SELLER/ADMIN',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    PRIMARY KEY (id),
    KEY idx_ticket_created (ticket_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后凭证附件';

-- 售后进度时间线（#143）。每次状态变更写一条，买卖双方与管理员按时间正序查看；
-- 仲裁期间双方补充材料记为 ARBITRATING -> ARBITRATING。
CREATE TABLE IF NOT EXISTS after_sale_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '事件ID',
    ticket_id       VARCHAR(32)  NOT NULL COMMENT '售后单ID',
    from_status     VARCHAR(24)  DEFAULT NULL COMMENT '变更前状态；创建时为 NULL',
    to_status       VARCHAR(24)  NOT NULL COMMENT '变更后状态',
    operator_id     VARCHAR(32)  DEFAULT NULL COMMENT '操作人；系统回调为 SYSTEM',
    remark          VARCHAR(255) DEFAULT NULL COMMENT '操作备注',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
    PRIMARY KEY (id),
    KEY idx_ticket_created (ticket_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后进度时间线';
