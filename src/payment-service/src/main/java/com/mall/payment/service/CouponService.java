package com.mall.payment.service;

import com.mall.payment.common.PageResult;
import com.mall.payment.dto.request.CouponCreateRequest;
import com.mall.payment.dto.request.OccupyCouponRequest;
import com.mall.payment.dto.response.AvailableCouponResponse;
import com.mall.payment.dto.response.ClaimableCouponResponse;
import com.mall.payment.dto.response.CouponResponse;
import com.mall.payment.dto.response.UserCouponResponse;

import java.util.List;

/**
 * 优惠券（#140）：领取、查询、计价试算与下单占用/释放。
 */
public interface CouponService {

    /** 领券中心：进行中且未领完的券模板，含当前用户是否已领 */
    List<ClaimableCouponResponse> claimable(String userId);

    /** 结算页可用券：该店铺下用户未使用的券，含门槛是否满足与差额 */
    List<AvailableCouponResponse> available(String userId, String shopId, Long amount);

    /** 领券：同券一人一张，领完/过期/停用给出明确错误 */
    UserCouponResponse claim(String userId, String couponId);

    /** 我的券：UNUSED/USED/EXPIRED（过期为展示态，不回写） */
    PageResult<UserCouponResponse> mine(String userId, String status, int page, int size);

    /** 计价试算（内部）：按用户券ID取券，不做占用 */
    UserCouponResponse detail(String userCouponId);

    /** 下单占用（内部）：条件 UPDATE，失败抛 60011 */
    UserCouponResponse occupy(OccupyCouponRequest request);

    /** 取消订单释放（内部）：USED -> UNUSED，已过期不返还；幂等 */
    void release(String orderId);

    PageResult<CouponResponse> adminList(int page, int size);

    CouponResponse adminCreate(String operatorId, CouponCreateRequest request);
}