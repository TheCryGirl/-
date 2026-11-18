package com.mall.payment.dto.response;

import com.mall.payment.common.PromotionScope;
import com.mall.payment.common.PromotionStatus;
import com.mall.payment.common.PromotionType;
import com.mall.payment.entity.Promotion;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 满减/满折活动信息（#140）。
 */
@Data
@Builder
public class PromotionResponse {

    private String promotionId;
    private PromotionScope scope;
    private String shopId;
    private PromotionType type;
    private Long threshold;
    private Long value;
    private Boolean stackable;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private PromotionStatus status;

    public static PromotionResponse from(Promotion promotion) {
        return PromotionResponse.builder()
                .promotionId(promotion.getPromotionId())
                .scope(promotion.getScope())
                .shopId(promotion.getShopId())
                .type(promotion.getType())
                .threshold(promotion.getThreshold())
                .value(promotion.getValue())
                .stackable(promotion.getStackable())
                .startAt(promotion.getStartAt())
                .endAt(promotion.getEndAt())
                .status(promotion.getStatus())
                .build();
    }
}