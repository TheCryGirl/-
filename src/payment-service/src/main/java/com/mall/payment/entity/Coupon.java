package com.mall.payment.entity;

import com.mall.payment.common.CouponType;
import com.mall.payment.common.PromotionStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 优惠券模板（#140），与 coupon 表一一对应。
 * FIXED 时 value 为面额（分）；PERCENT 时 value 为折扣百分比整数（85 = 8.5 折）。
 */
@Data
public class Coupon {

    private String couponId;
    private String shopId;
    private String name;
    private CouponType type;
    private Long value;
    private Long minAmount;
    private Integer totalQty;
    private Integer issuedQty;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private PromotionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Boolean isDeleted;
}