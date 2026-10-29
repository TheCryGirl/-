package com.mall.payment.entity;

import com.mall.payment.common.RefundStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 退款单，与 refund 表一一对应。
 */
@Data
public class Refund {

    private String refundId;
    private String paymentId;
    private String orderId;
    private String buyerId;
    private String sellerId;
    private Long amount;
    private String reason;
    private RefundStatus status;
    private String auditedBy;
    private String auditRemark;
    private LocalDateTime auditedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Boolean isDeleted;
}
