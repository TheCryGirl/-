package com.mall.payment.entity;

import com.mall.payment.common.PaymentChannel;
import com.mall.payment.common.PaymentStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付单，与 payment 表一一对应。
 */
@Data
public class Payment {

    private String paymentId;
    private String payNo;
    private String orderId;
    private String buyerId;
    private String sellerId;
    private PaymentChannel channel;
    private Long amount;
    private Long refundedAmount;
    private PaymentStatus status;
    private String idempotencyKey;
    private LocalDateTime paidAt;
    /** 支付后对账完成时间；为空表示待对账。 */
    private LocalDateTime reconciledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Boolean isDeleted;
}
