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

    /**
     * 内部/售后流程使用：允许显式指定卖家归属，避免历史支付单缺少 sellerId。
     */
    RefundResponse createRefund(String buyerId, String paymentId, RefundRequest request,
                                String sellerIdOverride);

    /**
     * 内部审核入口：调用方已经完成权限校验（自动退款、MOCK 工具）。
     */
    RefundResponse auditRefund(String refundId, boolean approved, String auditedBy, String remark);

    /**
     * 带角色校验的审核入口：SELLER 只能审核自己店铺的退款，ADMIN 全量。
     */
    RefundResponse auditRefund(String refundId, boolean approved, String auditedBy,
                               String role, String remark);

    RefundResponse getRefund(String userId, String role, String refundId);

    PageResult<RefundResponse> listRefunds(String userId, String role, String status, int page, int size);

    /**
     * 订单取消后收到支付成功回调时，按剩余可退金额自动退款。
     */
    RefundResponse autoRefund(Payment payment, String reason);
}
