package com.mall.payment.mapper;

import com.mall.payment.entity.Coupon;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠券模板数据访问（#140）。
 */
public interface CouponMapper {

    /** 进行中且未领完的券模板（领券中心用） */
    List<Coupon> selectClaimable(@Param("now") LocalDateTime now);

    int insert(Coupon coupon);

    Coupon selectById(@Param("couponId") String couponId);

    List<Coupon> selectByIds(@Param("couponIds") List<String> couponIds);

    /** 领取时原子加发行量：仅活动期内且未领完时生效，返回影响行数 */
    int incrementIssued(@Param("couponId") String couponId, @Param("now") LocalDateTime now);

    List<Coupon> selectPage(@Param("offset") int offset, @Param("size") int size);

    long count();
}