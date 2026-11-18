package com.mall.payment.mapper;

import com.mall.payment.entity.UserCoupon;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户持券数据访问（#140）。状态流转全部是条件 UPDATE，防并发重复占用。
 */
public interface UserCouponMapper {

    int insert(UserCoupon userCoupon);

    UserCoupon selectById(@Param("userCouponId") String userCouponId);

    UserCoupon selectByUserAndCoupon(@Param("userId") String userId,
                                     @Param("couponId") String couponId);

    List<UserCoupon> selectByUser(@Param("userId") String userId);

    List<UserCoupon> selectByOrderId(@Param("orderId") String orderId);

    /** 下单占用：UNUSED -> USED 并挂订单号，并发下只有一次成功 */
    int occupy(@Param("userCouponId") String userCouponId,
               @Param("orderId") String orderId,
               @Param("buyerId") String buyerId,
               @Param("now") LocalDateTime now);

    /** 释放：USED -> UNUSED，幂等；过期不返还由服务层在调用前判断 */
    int release(@Param("userCouponId") String userCouponId,
                @Param("now") LocalDateTime now);
}