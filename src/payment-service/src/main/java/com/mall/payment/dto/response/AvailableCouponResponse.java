package com.mall.payment.dto.response;

import com.mall.payment.common.UserCouponStatus;
import com.mall.payment.entity.Coupon;
import com.mall.payment.entity.UserCoupon;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 结算页可用券（#140）。usable=false 时 shortfall 为还差多少分才达门槛。
 */
@Data
@Builder
public class AvailableCouponResponse {

    private String userCouponId;
    private String couponId;
    private String shopId;
    private String name;
    private String type;
    private Long value;
    private Long minAmount;
    private boolean usable;
    private long shortfall;
    private UserCouponStatus status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime receivedAt;

    public static AvailableCouponResponse from(UserCoupon userCoupon, Coupon coupon,
                                               long amount, LocalDateTime now) {
        boolean expired = coupon.getEndAt() != null && coupon.getEndAt().isBefore(now);
        boolean started = coupon.getStartAt() == null || !coupon.getStartAt().isAfter(now);
        boolean minMet = amount >= coupon.getMinAmount();
        boolean usable = !expired && started && minMet;
        long shortfall = minMet ? 0L : coupon.getMinAmount() - amount;
        UserCouponStatus display = expired ? UserCouponStatus.EXPIRED : userCoupon.getStatus();
        return AvailableCouponResponse.builder()
                .userCouponId(userCoupon.getUserCouponId())
                .couponId(userCoupon.getCouponId())
                .shopId(coupon.getShopId())
                .name(coupon.getName())
                .type(coupon.getType().name())
                .value(coupon.getValue())
                .minAmount(coupon.getMinAmount())
                .usable(usable)
                .shortfall(shortfall)
                .status(display)
                .startAt(coupon.getStartAt())
                .endAt(coupon.getEndAt())
                .receivedAt(userCoupon.getReceivedAt())
                .build();
    }
}