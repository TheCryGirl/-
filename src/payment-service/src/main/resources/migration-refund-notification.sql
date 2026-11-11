-- ============================================================
-- 老库升级脚本：新增 refund_notification_task（退款后的外部通知任务）
--
-- 背景：
--   退款审核通过且支付单全额退款时，需要通知 order-service 置为已退款、
--   product-service 回补库存。原先是在事务提交后用事务同步回调直接调远程，
--   失败只记 error 日志，进程重启或下游长时间不可用时这笔通知就永久丢失。
--   改为与退款审核同事务落库两条任务，提交后立即投递，失败由调度独立重试。
--
--   代码侧通过 CREATE TABLE IF NOT EXISTS 自动建表（应用启动时执行 schema.sql），
--   所以这里只是给已有库手工补结构的等同脚本，不执行任何数据变更。
--
-- 用法：
--   mysql -u root -p mall_payment < src/payment-service/src/main/resources/migration-refund-notification.sql
--
-- 幂等性：
--   CREATE TABLE IF NOT EXISTS，重复运行不报错、不改已存在的表。
-- ============================================================

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
    UNIQUE KEY uk_order_event (order_id, event_type),
    KEY idx_status_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款通知任务';
