package com.mall.payment.dto.response;

import com.mall.payment.common.PaymentChannel;
import com.mall.payment.common.PaymentStatus;
import com.mall.payment.entity.Payment;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付单查询返回结构。
 */
@Data
@Builder
public class PaymentDetailResponse {

    private String paymentId;
    private String payNo;
    private String orderId;
    private String buyerId;
    private String sellerId;
    private PaymentChannel channel;
    private Long amount;
    private Long refundedAmount;
    private PaymentStatus status;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;

    public static PaymentDetailResponse from(Payment payment) {
        return PaymentDetailResponse.builder()
                .paymentId(payment.getPaymentId())
                .payNo(payment.getPayNo())
                .orderId(payment.getOrderId())
                .buyerId(payment.getBuyerId())
                .sellerId(payment.getSellerId())
                .channel(payment.getChannel())
                .amount(payment.getAmount())
                .refundedAmount(payment.getRefundedAmount())
                .status(payment.getStatus())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
