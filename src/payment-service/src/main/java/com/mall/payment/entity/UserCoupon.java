package com.mall.payment.entity;

import com.mall.payment.common.UserCouponStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户持券（#140），与 user_coupon 表一一对应。
 * 同一用户同一模板只能领一张（uk_user_coupon）；占用后挂 order_id，
 * 取消/退款成功释放；已过期的不返还。
 */
@Data
public class UserCoupon {

    private String userCouponId;
    private String couponId;
    private String userId;
    private UserCouponStatus status;
    private String orderId;
    private LocalDateTime receivedAt;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isDeleted;
}