package com.mall.payment.entity;

import com.mall.payment.common.CallbackProcessStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付回调流水，与 payment_callback 表一一对应。
 */
@Data
public class PaymentCallback {

    private Long callbackId;
    private String tradeNo;
    private String paymentId;
    private String rawPayload;
    private Boolean signVerified;
    private Long amount;
    private CallbackProcessStatus processStatus;
    private String errorMessage;
    private LocalDateTime receivedAt;
}
