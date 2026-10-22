package com.mall.payment.service;

import com.mall.payment.common.PageResult;
import com.mall.payment.dto.request.RefundRequest;
import com.mall.payment.dto.response.RefundResponse;
import com.mall.payment.entity.Payment;

/**
 * 退款申请与审核。
 */
public interface RefundService {

    RefundResponse createRefund(String buyerId, String paymentId, RefundRequest request);

    RefundResponse auditRefund(String refundId, boolean approved, String auditedBy, String remark);

    PageResult<RefundResponse> listRefunds(String userId, String role, String status, int page, int size);

    /**
     * 订单取消后收到支付成功回调时，按剩余可退金额自动退款。
     */
    RefundResponse autoRefund(Payment payment, String reason);
}
