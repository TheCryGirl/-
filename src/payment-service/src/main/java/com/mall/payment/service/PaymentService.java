package com.mall.payment.service;

import com.mall.payment.dto.request.CreatePaymentRequest;
import com.mall.payment.dto.response.CreatePaymentResponse;
import com.mall.payment.dto.response.PaymentDetailResponse;
import com.mall.payment.entity.Payment;

/**
 * 支付单创建与查询。
 */
public interface PaymentService {

    CreatePaymentResponse createPayment(String buyerId, String idempotencyKey, CreatePaymentRequest request);

    PaymentDetailResponse getPayment(String buyerId, String paymentId);

    PaymentDetailResponse getPaymentByOrderId(String buyerId, String orderId);

    /**
     * 供 MOCK 接口等内部场景使用，同时校验支付单归属。
     */
    Payment getPaymentEntityForBuyer(String buyerId, String paymentId);
}
