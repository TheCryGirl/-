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

    /**
     * 跨店合并结算（#135）：按主订单发起支付，金额用主订单 payAmount。
     */
    CreatePaymentResponse createMainPayment(String buyerId, String idempotencyKey,
                                            String mainOrderId, com.mall.payment.common.ChannelType channel);

    PaymentDetailResponse getPayment(String buyerId, String paymentId);

    PaymentDetailResponse getPaymentByOrderId(String buyerId, String orderId);

    /**
     * 供 MOCK 接口等内部场景使用，同时校验支付单归属。
     */
    Payment getPaymentEntityForBuyer(String buyerId, String paymentId);
}
