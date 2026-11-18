package com.mall.payment.controller;

import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.PageResult;
import com.mall.payment.common.Result;
import com.mall.payment.dto.request.CouponCreateRequest;
import com.mall.payment.dto.request.OccupyCouponRequest;
import com.mall.payment.dto.request.ReleaseCouponRequest;
import com.mall.payment.dto.response.AvailableCouponResponse;
import com.mall.payment.dto.response.ClaimableCouponResponse;
import com.mall.payment.dto.response.CouponResponse;
import com.mall.payment.dto.response.UserCouponResponse;
import com.mall.payment.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 优惠券接口（#140）。买家领券/查券/结算可用券，管理员维护券模板；
 * 占用与释放为内部接口，供 order-service 计价与取消订单调用。
 */
@RestController
@RequestMapping({"/coupons", "/api/coupons"})
@RequiredArgsConstructor
public class CouponController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final CouponService couponService;

    /** 领券中心：进行中且未领完的券模板，含当前用户是否已领 */
    @GetMapping("/claimable")
    public Result<List<ClaimableCouponResponse>> claimable(
            @RequestHeader("X-User-Id") String userId) {
        return Result.success(couponService.claimable(userId));
    }

    /** 结算页可用券：该店铺下用户未使用的券，含门槛是否满足与差额 */
    @GetMapping("/available")
    public Result<List<AvailableCouponResponse>> available(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("shopId") String shopId,
            @RequestParam(value = "amount", required = false) Long amount) {
        return Result.success(couponService.available(userId, shopId, amount));
    }

    /** 领券 */
    @PostMapping("/{couponId}/claim")
    public Result<UserCouponResponse> claim(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("couponId") String couponId) {
        return Result.success(couponService.claim(userId, couponId));
    }

    /** 我的券：UNUSED/USED/EXPIRED */
    @GetMapping("/mine")
    public Result<PageResult<UserCouponResponse>> mine(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(couponService.mine(userId, status, page, size));
    }

    /** 管理员：券模板列表 */
    @GetMapping
    public Result<PageResult<CouponResponse>> adminList(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(userId, role);
        return Result.success(couponService.adminList(page, size));
    }

    /** 管理员：创建券模板 */
    @PostMapping
    public Result<CouponResponse> adminCreate(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody CouponCreateRequest request) {
        requireAdmin(userId, role);
        return Result.success(couponService.adminCreate(userId, request));
    }

    private void requireAdmin(String userId, String role) {
        if (!ROLE_ADMIN.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限维护优惠券");
        }
    }

    /**
     * 内部接口（#140）：供 order-service 计价试算、下单占用与取消释放。
     * 网关拦截 /internal 路径，服务间 Feign 携带 X-Internal-Token 直连。
     */
    @RestController
    @RequestMapping("/internal/coupons")
    @RequiredArgsConstructor
    public static class InternalCouponController {

        private final CouponService couponService;

        /** 计价试算：按用户券ID取券，只读不占用 */
        @GetMapping("/{userCouponId}")
        public Result<UserCouponResponse> detail(
                @PathVariable("userCouponId") String userCouponId) {
            return Result.success(couponService.detail(userCouponId));
        }

        /** 下单占用：条件 UPDATE，并发下只成功一次 */
        @PostMapping("/occupy")
        public Result<UserCouponResponse> occupy(@Valid @RequestBody OccupyCouponRequest request) {
            return Result.success(couponService.occupy(request));
        }

        /** 取消订单释放：已过期不返还，幂等 */
        @PostMapping("/release")
        public Result<Void> release(@Valid @RequestBody ReleaseCouponRequest request) {
            couponService.release(request.getOrderId());
            return Result.success(null);
        }
    }
}