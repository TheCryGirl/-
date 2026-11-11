package com.mall.payment.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 退款后的外部通知任务，与 refund_notification_task 表一一对应。
 *
 * <p>两个事件各占一条：ORDER_REFUNDED（通知订单已退款）与 STOCK_RESTOCK（回补库存）。
 * 同一订单同一事件唯一，重复审核不会重复插入。
 */
@Data
public class RefundNotificationTask {

    private Long eventId;
    private String orderId;
    private String eventType;
    private String status;
    private Integer attempts;
    private LocalDateTime nextRetryAt;
    private LocalDateTime leaseUntil;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
