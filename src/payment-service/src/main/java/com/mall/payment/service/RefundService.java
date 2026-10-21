package com.mall.payment.service;

import com.mall.payment.dto.request.RefundRequest;
import com.mall.payment.dto.response.PageResponse;
import com.mall.payment.dto.response.RefundResponse;
import com.mall.payment.entity.Payment;

/**
 * 退款申请与审核。
 */
public interface RefundService {

    RefundResponse createRefund(String buyerId, String paymentId, RefundRequest request);

    RefundResponse auditRefund(String refundId, boolean approved, String auditedBy, String remark);

    /**
     * 卖家与管理员查看退款单列表，status 为空时查全部。
     */
    PageResponse<RefundResponse> listRefunds(int page, int size, String status);

    /**
     * 订单取消后收到支付成功回调时，按剩余可退金额自动退款。
     */
    RefundResponse autoRefund(Payment payment, String reason);
}
