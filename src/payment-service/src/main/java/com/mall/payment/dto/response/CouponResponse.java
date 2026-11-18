package com.mall.payment.dto.response;

import com.mall.payment.common.CouponType;
import com.mall.payment.common.PromotionStatus;
import com.mall.payment.entity.Coupon;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 优惠券模板信息（#140）。
 */
@Data
@Builder
public class CouponResponse {

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

    public static CouponResponse from(Coupon coupon) {
        return CouponResponse.builder()
                .couponId(coupon.getCouponId())
                .shopId(coupon.getShopId())
                .name(coupon.getName())
                .type(coupon.getType())
                .value(coupon.getValue())
                .minAmount(coupon.getMinAmount())
                .totalQty(coupon.getTotalQty())
                .issuedQty(coupon.getIssuedQty())
                .startAt(coupon.getStartAt())
                .endAt(coupon.getEndAt())
                .status(coupon.getStatus())
                .build();
    }
}