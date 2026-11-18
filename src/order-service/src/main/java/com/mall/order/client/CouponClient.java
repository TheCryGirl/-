package com.mall.order.client;

import com.mall.order.client.dto.OccupyCouponRequestDTO;
import com.mall.order.client.dto.ReleaseCouponRequestDTO;
import com.mall.order.client.dto.UserCouponDTO;
import com.mall.order.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 优惠券内部接口客户端（#140）：计价试算、下单占用与取消释放。
 * 均在 payment-service，服务间 Feign 直连并携带 X-Internal-Token。
 */
@FeignClient(name = "payment-service", url = "${services.payment-url:}", contextId = "couponClient")
public interface CouponClient {

    /** 计价试算：按用户券ID取券，只读不占用 */
    @GetMapping("/internal/coupons/{userCouponId}")
    Result<UserCouponDTO> detail(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @PathVariable("userCouponId") String userCouponId);

    /** 下单占用：条件 UPDATE，并发下只成功一次 */
    @PostMapping("/internal/coupons/occupy")
    Result<UserCouponDTO> occupy(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestBody OccupyCouponRequestDTO request);

    /** 取消订单释放：已过期不返还，幂等 */
    @PostMapping("/internal/coupons/release")
    Result<Void> release(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestBody ReleaseCouponRequestDTO request);
}
