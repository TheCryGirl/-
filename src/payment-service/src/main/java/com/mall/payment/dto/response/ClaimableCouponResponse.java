package com.mall.payment.dto.response;

import com.mall.payment.entity.Coupon;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 领券中心的可领券（#140）。claimed 表示当前用户是否已领过（同一模板一人一张）。
 */
@Data
@Builder
public class ClaimableCouponResponse {

    private String couponId;
    private String shopId;
    private String name;
    private String type;
    private Long value;
    private Long minAmount;
    private Integer totalQty;
    private Integer issuedQty;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private boolean claimed;

    public static ClaimableCouponResponse from(Coupon coupon, boolean claimed) {
        return ClaimableCouponResponse.builder()
                .couponId(coupon.getCouponId())
                .shopId(coupon.getShopId())
                .name(coupon.getName())
                .type(coupon.getType().name())
                .value(coupon.getValue())
                .minAmount(coupon.getMinAmount())
                .totalQty(coupon.getTotalQty())
                .issuedQty(coupon.getIssuedQty())
                .startAt(coupon.getStartAt())
                .endAt(coupon.getEndAt())
                .claimed(claimed)
                .build();
    }
}
