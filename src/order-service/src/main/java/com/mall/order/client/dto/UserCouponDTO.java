package com.mall.order.client.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户优惠券信息（#140，payment-service 内部接口返回）。
 * status 为展示状态：UNUSED/USED/EXPIRED（UNUSED 但已过期按 EXPIRED 返回）。
 */
@Data
public class UserCouponDTO {

    private String userCouponId;
    private String couponId;
    private String shopId;
    private String userId;
    private String name;
    private String type;
    private Long value;
    private Long minAmount;
    private String status;
    private String orderId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime receivedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime usedAt;
}
