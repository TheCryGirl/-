package com.mall.payment.service;

import com.mall.payment.dto.request.MockDuplicateRequest;
import com.mall.payment.dto.request.MockPayRequest;
import com.mall.payment.dto.request.MockRefundRequest;
import com.mall.payment.dto.response.MockDuplicateResponse;
import com.mall.payment.dto.response.MockPayResponse;
import com.mall.payment.dto.response.RefundResponse;

/**
 * 开发期 MOCK 支付工具，必须走真实的回调和退款流程。
 */
public interface MockPaymentService {

    MockPayResponse mockPay(String buyerId, MockPayRequest request);

    MockDuplicateResponse duplicateCallback(MockDuplicateRequest request);

    RefundResponse mockRefund(String buyerId, MockRefundRequest request);
}
