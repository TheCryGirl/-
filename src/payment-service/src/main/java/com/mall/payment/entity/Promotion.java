package com.mall.payment.entity;

import com.mall.payment.common.PromotionScope;
import com.mall.payment.common.PromotionStatus;
import com.mall.payment.common.PromotionType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 满减/满折活动（#140），与 promotion 表一一对应。
 * FULL_REDUCE 满 threshold 减 value 分；FULL_DISCOUNT 满 threshold 打 value 折（百分比整数）。
 * stackable=false 时不能与优惠券同时使用。
 */
@Data
public class Promotion {

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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Boolean isDeleted;
}