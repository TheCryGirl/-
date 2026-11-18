package com.mall.payment.service.impl;

import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.IdGenerator;
import com.mall.payment.common.PageResult;
import com.mall.payment.common.PromotionStatus;
import com.mall.payment.common.UserCouponStatus;
import com.mall.payment.dto.request.CouponCreateRequest;
import com.mall.payment.dto.request.OccupyCouponRequest;
import com.mall.payment.dto.response.AvailableCouponResponse;
import com.mall.payment.dto.response.ClaimableCouponResponse;
import com.mall.payment.dto.response.CouponResponse;
import com.mall.payment.dto.response.UserCouponResponse;
import com.mall.payment.entity.Coupon;
import com.mall.payment.entity.UserCoupon;
import com.mall.payment.mapper.CouponMapper;
import com.mall.payment.mapper.UserCouponMapper;
import com.mall.payment.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 优惠券实现（#140）。
 *
 * <p>并发安全：领取的发行量、下单的占用都是条件 UPDATE，影响行数为 0 即失败；
 * 同券一人一张靠 uk_user_coupon 唯一键，重复领取整体回滚（含发行量自增）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;

    @Override
    public List<ClaimableCouponResponse> claimable(String userId) {
        Set<String> claimedIds = userCouponMapper.selectByUser(userId).stream()
                .map(UserCoupon::getCouponId)
                .collect(Collectors.toSet());
        return couponMapper.selectClaimable(LocalDateTime.now()).stream()
                .map(coupon -> ClaimableCouponResponse.from(coupon, claimedIds.contains(coupon.getCouponId())))
                .toList();
    }

    @Override
    public List<AvailableCouponResponse> available(String userId, String shopId, Long amount) {
        if (shopId == null || shopId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "店铺ID不能为空");
        }
        long safeAmount = amount == null ? 0L : Math.max(0L, amount);
        LocalDateTime now = LocalDateTime.now();
        Map<String, Coupon> coupons = couponMap(userCouponMapper.selectByUser(userId));
        List<AvailableCouponResponse> result = new ArrayList<>();
        for (UserCoupon userCoupon : userCouponMapper.selectByUser(userId)) {
            Coupon coupon = coupons.get(userCoupon.getCouponId());
            if (coupon == null || !shopId.equals(coupon.getShopId())) {
                continue;
            }
            if (userCoupon.getStatus() != UserCouponStatus.UNUSED) {
                continue;
            }
            result.add(AvailableCouponResponse.from(userCoupon, coupon, safeAmount, now));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserCouponResponse claim(String userId, String couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BizException(ErrorCode.COUPON_NOT_FOUND, "优惠券不存在");
        }
        if (userCouponMapper.selectByUserAndCoupon(userId, couponId) != null) {
            throw new BizException(ErrorCode.COUPON_ALREADY_CLAIMED, "已领取过该优惠券，不能重复领取");
        }
        int incremented = couponMapper.incrementIssued(couponId, LocalDateTime.now());
        if (incremented == 0) {
            Coupon latest = couponMapper.selectById(couponId);
            if (latest == null) {
                throw new BizException(ErrorCode.COUPON_NOT_FOUND, "优惠券不存在");
            }
            if (latest.getStatus() != PromotionStatus.ACTIVE) {
                throw new BizException(ErrorCode.COUPON_NOT_AVAILABLE, "优惠券已停用");
            }
            if (latest.getIssuedQty() >= latest.getTotalQty()) {
                throw new BizException(ErrorCode.COUPON_OUT_OF_STOCK, "优惠券已被领完");
            }
            throw new BizException(ErrorCode.COUPON_NOT_AVAILABLE, "优惠券不在可领取时间内");
        }

        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserCouponId(IdGenerator.userCouponId());
        userCoupon.setCouponId(couponId);
        userCoupon.setUserId(userId);
        userCoupon.setStatus(UserCouponStatus.UNUSED);
        userCoupon.setIsDeleted(false);
        try {
            userCouponMapper.insert(userCoupon);
        } catch (DuplicateKeyException e) {
            // 同事务回滚已自增的发行量，再给用户明确提示
            throw new BizException(ErrorCode.COUPON_ALREADY_CLAIMED, "已领取过该优惠券，不能重复领取");
        }
        log.info("领券成功, userId={}, couponId={}, userCouponId={}",
                userId, couponId, userCoupon.getUserCouponId());
        return UserCouponResponse.from(userCoupon, coupon, LocalDateTime.now());
    }

    @Override
    public PageResult<UserCouponResponse> mine(String userId, String status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        UserCouponStatus filter = parseStatus(status);
        LocalDateTime now = LocalDateTime.now();
        Map<String, Coupon> coupons = couponMap(userCouponMapper.selectByUser(userId));
        List<UserCouponResponse> all = new ArrayList<>();
        for (UserCoupon userCoupon : userCouponMapper.selectByUser(userId)) {
            Coupon coupon = coupons.get(userCoupon.getCouponId());
            if (coupon == null) {
                continue;
            }
            UserCouponResponse response = UserCouponResponse.from(userCoupon, coupon, now);
            if (filter != null && filter != response.getStatus()) {
                continue;
            }
            all.add(response);
        }
        int from = (safePage - 1) * safeSize;
        List<UserCouponResponse> chunk = all.subList(Math.min(from, all.size()),
                Math.min(from + safeSize, all.size()));
        return PageResult.of(chunk, all.size(), safePage, safeSize);
    }

    @Override
    public UserCouponResponse detail(String userCouponId) {
        UserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if (userCoupon == null) {
            throw new BizException(ErrorCode.COUPON_NOT_FOUND, "优惠券不存在");
        }
        Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
        if (coupon == null) {
            throw new BizException(ErrorCode.COUPON_NOT_FOUND, "优惠券模板不存在");
        }
        return UserCouponResponse.from(userCoupon, coupon, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserCouponResponse occupy(OccupyCouponRequest request) {
        UserCoupon userCoupon = userCouponMapper.selectById(request.getUserCouponId());
        if (userCoupon == null) {
            throw new BizException(ErrorCode.COUPON_NOT_FOUND, "优惠券不存在");
        }
        if (!request.getBuyerId().equals(userCoupon.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权使用该优惠券");
        }
        Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
        if (coupon == null) {
            throw new BizException(ErrorCode.COUPON_NOT_FOUND, "优惠券模板不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getEndAt() != null && coupon.getEndAt().isBefore(now)) {
            throw new BizException(ErrorCode.COUPON_NOT_AVAILABLE, "优惠券已过期");
        }
        if (coupon.getStartAt() != null && coupon.getStartAt().isAfter(now)) {
            throw new BizException(ErrorCode.COUPON_NOT_AVAILABLE, "优惠券未到使用时间");
        }
        if (coupon.getStatus() != PromotionStatus.ACTIVE) {
            throw new BizException(ErrorCode.COUPON_NOT_AVAILABLE, "优惠券已停用");
        }
        int occupied = userCouponMapper.occupy(
                request.getUserCouponId(), request.getOrderId(), request.getBuyerId(), now);
        if (occupied == 0) {
            throw new BizException(ErrorCode.COUPON_ALREADY_USED, "优惠券已被使用，请刷新后重试");
        }
        log.info("优惠券占用成功, userCouponId={}, orderId={}, buyerId={}",
                request.getUserCouponId(), request.getOrderId(), request.getBuyerId());
        UserCoupon latest = userCouponMapper.selectById(request.getUserCouponId());
        return UserCouponResponse.from(latest, coupon, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(String orderId) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Coupon> coupons = couponMap(userCouponMapper.selectByOrderId(orderId));
        for (UserCoupon userCoupon : userCouponMapper.selectByOrderId(orderId)) {
            Coupon coupon = coupons.get(userCoupon.getCouponId());
            // 已过期的不返还（#140 验收）
            if (coupon != null && coupon.getEndAt() != null && coupon.getEndAt().isBefore(now)) {
                log.info("优惠券已过期不返还, userCouponId={}, orderId={}",
                        userCoupon.getUserCouponId(), orderId);
                continue;
            }
            int released = userCouponMapper.release(userCoupon.getUserCouponId(), now);
            if (released > 0) {
                log.info("优惠券已释放, userCouponId={}, orderId={}",
                        userCoupon.getUserCouponId(), orderId);
            }
        }
    }

    @Override
    public PageResult<CouponResponse> adminList(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long total = couponMapper.count();
        List<CouponResponse> list = couponMapper
                .selectPage((safePage - 1) * safeSize, safeSize)
                .stream().map(CouponResponse::from).toList();
        return PageResult.of(list, total, safePage, safeSize);
    }

    @Override
    public CouponResponse adminCreate(String operatorId, CouponCreateRequest request) {
        if (request.getStartAt().isAfter(request.getEndAt())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "开始时间不能晚于结束时间");
        }
        Coupon coupon = new Coupon();
        coupon.setCouponId(IdGenerator.couponId());
        coupon.setShopId(request.getShopId());
        coupon.setName(request.getName());
        coupon.setType(request.getType());
        coupon.setValue(request.getValue());
        coupon.setMinAmount(request.getMinAmount());
        coupon.setTotalQty(request.getTotalQty());
        coupon.setIssuedQty(0);
        coupon.setStartAt(request.getStartAt());
        coupon.setEndAt(request.getEndAt());
        coupon.setStatus(PromotionStatus.ACTIVE);
        coupon.setCreatedBy(operatorId);
        coupon.setUpdatedBy(operatorId);
        coupon.setIsDeleted(false);
        couponMapper.insert(coupon);
        return CouponResponse.from(couponMapper.selectById(coupon.getCouponId()));
    }

    private Map<String, Coupon> couponMap(List<UserCoupon> userCoupons) {
        List<String> couponIds = userCoupons.stream()
                .map(UserCoupon::getCouponId).distinct().collect(Collectors.toList());
        if (couponIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Coupon> map = new LinkedHashMap<>();
        for (Coupon coupon : couponMapper.selectByIds(couponIds)) {
            map.put(coupon.getCouponId(), coupon);
        }
        return map;
    }

    private UserCouponStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return UserCouponStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "券状态不合法");
        }
    }
}