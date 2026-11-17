package com.mall.payment.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 退款回调流水，与 refund_callback 表一一对应（#137）。
 */
@Data
public class RefundCallback {

    private Long callbackId;
    private String channel;
    private String notifyId;
    private String refundId;
    private String rawPayload;
    private Boolean signVerified;
    private Long amount;
    private String processStatus;
    private String errorMessage;
    private LocalDateTime receivedAt;
}