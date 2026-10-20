package com.mall.payment.dto.response;

import com.mall.payment.common.RefundStatus;
import com.mall.payment.entity.Refund;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 退款单返回结构。
 */
@Data
@Builder
public class RefundResponse {

    private String refundId;
    private String paymentId;
    private String orderId;
    private Long amount;
    private String reason;
    private RefundStatus status;
    private String auditedBy;
    private LocalDateTime auditedAt;
    private LocalDateTime createdAt;

    public static RefundResponse from(Refund refund) {
        return RefundResponse.builder()
                .refundId(refund.getRefundId())
                .paymentId(refund.getPaymentId())
                .orderId(refund.getOrderId())
                .amount(refund.getAmount())
                .reason(refund.getReason())
                .status(refund.getStatus())
                .auditedBy(refund.getAuditedBy())
                .auditedAt(refund.getAuditedAt())
                .createdAt(refund.getCreatedAt())
                .build();
    }
}
