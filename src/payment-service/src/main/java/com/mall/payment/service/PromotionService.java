package com.mall.payment.service;

import com.mall.payment.common.PageResult;
import com.mall.payment.dto.request.PromotionCreateRequest;
import com.mall.payment.dto.request.PromotionUpdateRequest;
import com.mall.payment.dto.response.PromotionResponse;

import java.util.List;

/**
 * 满减/满折活动（#140）：管理端配置与计价链试算读取。
 */
public interface PromotionService {

    PageResult<PromotionResponse> adminList(String status, int page, int size);

    PromotionResponse adminCreate(String operatorId, PromotionCreateRequest request);

    PromotionResponse adminUpdate(String operatorId, String promotionId, PromotionUpdateRequest request);

    /** 计价链试算（内部）：当前生效中的活动 */
    List<PromotionResponse> active();
}