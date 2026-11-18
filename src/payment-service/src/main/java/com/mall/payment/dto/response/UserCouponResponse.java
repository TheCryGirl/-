package com.mall.payment.dto.response;

import com.mall.payment.common.UserCouponStatus;
import com.mall.payment.entity.Coupon;
import com.mall.payment.entity.UserCoupon;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户持券信息（#140）。status 为展示状态：UNUSED 但已过期的券按 EXPIRED 返回。
 */
@Data
@Builder
public class UserCouponResponse {

    private String userCouponId;
    private String couponId;
    private String shopId;
    private String userId;
    private String name;
    private String type;
    private Long value;
    private Long minAmount;
    private UserCouponStatus status;
    private String orderId;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime receivedAt;
    private LocalDateTime usedAt;

    public static UserCouponResponse from(UserCoupon userCoupon, Coupon coupon, LocalDateTime now) {
        UserCouponStatus display = userCoupon.getStatus();
        if (display == UserCouponStatus.UNUSED && coupon != null
                && coupon.getEndAt() != null && coupon.getEndAt().isBefore(now)) {
            display = UserCouponStatus.EXPIRED;
        }
        return UserCouponResponse.builder()
                .userCouponId(userCoupon.getUserCouponId())
                .couponId(userCoupon.getCouponId())
                .shopId(coupon == null ? null : coupon.getShopId())
                .userId(userCoupon.getUserId())
                .name(coupon == null ? null : coupon.getName())
                .type(coupon == null ? null : coupon.getType().name())
                .value(coupon == null ? null : coupon.getValue())
                .minAmount(coupon == null ? null : coupon.getMinAmount())
                .status(display)
                .orderId(userCoupon.getOrderId())
                .startAt(coupon == null ? null : coupon.getStartAt())
                .endAt(coupon == null ? null : coupon.getEndAt())
                .receivedAt(userCoupon.getReceivedAt())
                .usedAt(userCoupon.getUsedAt())
                .build();
    }
}