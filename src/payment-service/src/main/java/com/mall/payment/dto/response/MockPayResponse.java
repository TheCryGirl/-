package com.mall.payment.dto.response;

import com.mall.payment.common.PaymentStatus;
import com.mall.payment.entity.Payment;
import lombok.Builder;
import lombok.Data;

/**
 * MOCK 模拟支付的返回结构，tradeNo 可用于调用重复回调节口。
 */
@Data
@Builder
public class MockPayResponse {

    private String tradeNo;
    private String paymentId;
    private String payNo;
    private Long amount;
    private PaymentStatus status;

    public static MockPayResponse from(Payment payment, String tradeNo) {
        return MockPayResponse.builder()
                .tradeNo(tradeNo)
                .paymentId(payment.getPaymentId())
                .payNo(payment.getPayNo())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .build();
    }
}
