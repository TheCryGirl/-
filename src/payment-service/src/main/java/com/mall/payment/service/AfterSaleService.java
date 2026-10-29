package com.mall.payment.service;

import com.mall.payment.common.PageResult;
import com.mall.payment.dto.request.CreateAfterSaleRequest;
import com.mall.payment.dto.response.AfterSaleResponse;

/**
 * 分类型售后：仅退款、退货退款、换货。
 */
public interface AfterSaleService {

    AfterSaleResponse create(String buyerId, String idempotencyKey, CreateAfterSaleRequest request);

    PageResult<AfterSaleResponse> list(String userId, String role, String status, String type,
                                       String orderId, int page, int size);

    AfterSaleResponse detail(String userId, String role, String ticketId);

    AfterSaleResponse audit(String ticketId, String operatorId, String role,
                            boolean approved, String remark);

    AfterSaleResponse shipReturn(String ticketId, String buyerId, String trackingNo);

    AfterSaleResponse receiveReturn(String ticketId, String operatorId, String role, String remark);

    AfterSaleResponse reshipExchange(String ticketId, String operatorId, String role,
                                     String trackingNo, String remark);
}
