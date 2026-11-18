package com.mall.payment.service;

import com.mall.payment.client.OrderClient;
import com.mall.payment.client.ProductClient;
import com.mall.payment.client.dto.StockConfirmRequest;
import com.mall.payment.common.BizException;
import com.mall.payment.common.CouponType;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.PageResult;
import com.mall.payment.common.PromotionScope;
import com.mall.payment.common.PromotionStatus;
import com.mall.payment.common.PromotionType;
import com.mall.payment.common.Result;
import com.mall.payment.common.UserCouponStatus;
import com.mall.payment.dto.request.CouponCreateRequest;
import com.mall.payment.dto.request.OccupyCouponRequest;
import com.mall.payment.dto.request.PromotionCreateRequest;
import com.mall.payment.dto.request.PromotionUpdateRequest;
import com.mall.payment.dto.request.RefundRequest;
import com.mall.payment.dto.response.AvailableCouponResponse;
import com.mall.payment.dto.response.ClaimableCouponResponse;
import com.mall.payment.dto.response.CouponResponse;
import com.mall.payment.dto.response.PromotionResponse;
import com.mall.payment.dto.response.UserCouponResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * #140 促销工具（优惠券 / 满减满折活动）集成测试。
 *
 * <p>对应验收：重复领取被拒、领完报错、领取时间外报错；结算页可用券按门槛给差额；
 * 我的券三态展示（过期为展示态不回写）；下单占用并发下只有一次成功；
 * 释放幂等且已过期不返还；退款成功返还占用券；活动增改查与生效清单。
 */
@SpringBootTest
class CouponPromotionIntegrationTest {

    private static final String PAYMENT_ID = "PAYTEST0000000000140";
    private static final String PAY_NO = "P20261118000140";
    private static final String ORDER_ID = "O14001";
    private static final String BUYER_ID = "U14001";
    private static final String OTHER_USER_ID = "U14002";
    private static final String SELLER_ID = "S14001";
    private static final String ADMIN_ID = "ADMIN01";
    private static final long PAID_AMOUNT = 11800L;

    @Autowired
    private CouponService couponService;

    @Autowired
    private PromotionService promotionService;

    @Autowired
    private RefundService refundService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private OrderClient orderClient;

    @MockBean
    private ProductClient productClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM user_coupon");
        jdbcTemplate.update("DELETE FROM coupon");
        jdbcTemplate.update("DELETE FROM promotion");
        jdbcTemplate.update("DELETE FROM refund_notification_task");
        jdbcTemplate.update("DELETE FROM after_sale_ticket");
        jdbcTemplate.update("DELETE FROM refund");
        jdbcTemplate.update("DELETE FROM payment_callback");
        jdbcTemplate.update("DELETE FROM payment");
        insertPaidPayment();
        when(orderClient.markRefunded(ORDER_ID)).thenReturn(Result.success(null));
        when(productClient.refundRestock(any(StockConfirmRequest.class))).thenReturn(Result.success(null));
    }

    @Test
    void claimIsLimitedToOnePerUserAndAvailableReflectsThreshold() {
        CouponResponse coupon = createCoupon(CouponType.FIXED, 500L, 5000L, 10,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
        UserCouponResponse claimed = couponService.claim(BUYER_ID, coupon.getCouponId());
        assertEquals(UserCouponStatus.UNUSED, claimed.getStatus());
        assertEquals(coupon.getCouponId(), claimed.getCouponId());
        assertEquals(SELLER_ID, claimed.getShopId());

        BizException duplicate = assertThrows(BizException.class,
                () -> couponService.claim(BUYER_ID, coupon.getCouponId()));
        assertEquals(ErrorCode.COUPON_ALREADY_CLAIMED, duplicate.getCode());

        // 金额低于门槛：不可用并给出差额
        List<AvailableCouponResponse> below = couponService.available(BUYER_ID, SELLER_ID, 4999L);
        assertEquals(1, below.size());
        assertFalse(below.get(0).isUsable());
        assertEquals(1L, below.get(0).getShortfall());

        // 金额达到门槛：可用
        List<AvailableCouponResponse> met = couponService.available(BUYER_ID, SELLER_ID, 5000L);
        assertEquals(1, met.size());
        assertTrue(met.get(0).isUsable());
        assertEquals(0L, met.get(0).getShortfall());

        // 别的店铺不可见
        assertTrue(couponService.available(BUYER_ID, "S99999", 5000L).isEmpty());
    }

    @Test
    void claimReportsOutOfStockForOtherUser() {
        CouponResponse coupon = createCoupon(CouponType.FIXED, 500L, 0L, 1,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
        couponService.claim(BUYER_ID, coupon.getCouponId());
        BizException outOfStock = assertThrows(BizException.class,
                () -> couponService.claim(OTHER_USER_ID, coupon.getCouponId()));
        assertEquals(ErrorCode.COUPON_OUT_OF_STOCK, outOfStock.getCode());
    }

    @Test
    void claimOutsideWindowIsRejected() {
        CouponResponse expired = createCoupon(CouponType.FIXED, 500L, 0L, 10,
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1));
        BizException expiredEx = assertThrows(BizException.class,
                () -> couponService.claim(BUYER_ID, expired.getCouponId()));
        assertEquals(ErrorCode.COUPON_NOT_AVAILABLE, expiredEx.getCode());

        CouponResponse notStarted = createCoupon(CouponType.FIXED, 500L, 0L, 10,
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusDays(7));
        BizException notStartedEx = assertThrows(BizException.class,
                () -> couponService.claim(BUYER_ID, notStarted.getCouponId()));
        assertEquals(ErrorCode.COUPON_NOT_AVAILABLE, notStartedEx.getCode());
    }

    @Test
    void claimInactiveCouponIsRejected() {
        CouponResponse coupon = createCoupon(CouponType.FIXED, 500L, 0L, 10,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
        jdbcTemplate.update("UPDATE coupon SET status = 'INACTIVE' WHERE coupon_id = ?",
                coupon.getCouponId());
        BizException inactive = assertThrows(BizException.class,
                () -> couponService.claim(BUYER_ID, coupon.getCouponId()));
        assertEquals(ErrorCode.COUPON_NOT_AVAILABLE, inactive.getCode());
    }

    @Test
    void claimableListsOngoingCouponsWithClaimedFlag() {
        CouponResponse coupon = createCoupon(CouponType.FIXED, 500L, 5000L, 10,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
        // 未开始与领完的不出现
        createCoupon(CouponType.FIXED, 100L, 0L, 10,
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusDays(7));
        CouponResponse soldOut = createCoupon(CouponType.FIXED, 100L, 0L, 1,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
        couponService.claim(OTHER_USER_ID, soldOut.getCouponId());

        List<ClaimableCouponResponse> list = couponService.claimable(BUYER_ID);
        List<String> claimableIds = list.stream().map(ClaimableCouponResponse::getCouponId).toList();
        assertTrue(claimableIds.contains(coupon.getCouponId()));
        assertFalse(claimableIds.contains(soldOut.getCouponId()));
        ClaimableCouponResponse item = list.stream()
                .filter(c -> c.getCouponId().equals(coupon.getCouponId())).findFirst().orElseThrow();
        assertFalse(item.isClaimed());

        couponService.claim(BUYER_ID, coupon.getCouponId());
        ClaimableCouponResponse after = couponService.claimable(BUYER_ID).stream()
                .filter(c -> c.getCouponId().equals(coupon.getCouponId())).findFirst().orElseThrow();
        assertTrue(after.isClaimed());
    }

    @Test
    void mineShowsThreeStatesAndExpiredIsDisplayOnly() {
        CouponResponse normal = createCoupon(CouponType.FIXED, 500L, 0L, 10,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
        CouponResponse expiresSoon = createCoupon(CouponType.PERCENT, 85L, 0L, 10,
                LocalDateTime.now().minusDays(2), LocalDateTime.now().plusDays(7));
        couponService.claim(BUYER_ID, normal.getCouponId());
        couponService.claim(BUYER_ID, expiresSoon.getCouponId());
        // 领取后券过期：展示态应为 EXPIRED，库里不回写
        jdbcTemplate.update("UPDATE coupon SET end_at = ? WHERE coupon_id = ?",
                LocalDateTime.now().minusMinutes(1), expiresSoon.getCouponId());

        PageResult<UserCouponResponse> unused = couponService.mine(BUYER_ID, "UNUSED", 1, 10);
        assertEquals(1, unused.getTotal());
        assertEquals(UserCouponStatus.UNUSED, unused.getList().get(0).getStatus());

        PageResult<UserCouponResponse> expired = couponService.mine(BUYER_ID, "EXPIRED", 1, 10);
        assertEquals(1, expired.getTotal());
        assertEquals(UserCouponStatus.EXPIRED, expired.getList().get(0).getStatus());
        // 展示态不回写：库里仍是 UNUSED
        String stored = jdbcTemplate.queryForObject(
                "SELECT status FROM user_coupon WHERE user_id = ? AND coupon_id = ?",
                String.class, BUYER_ID, expiresSoon.getCouponId());
        assertEquals("UNUSED", stored);

        assertEquals(0, couponService.mine(BUYER_ID, "USED", 1, 10).getTotal());
        assertEquals(2, couponService.mine(BUYER_ID, null, 1, 10).getTotal());
    }

    @Test
    void occupyIsSafeUnderConcurrency() throws Exception {
        CouponResponse coupon = createCoupon(CouponType.FIXED, 500L, 0L, 10,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
        String userCouponId = couponService.claim(BUYER_ID, coupon.getCouponId()).getUserCouponId();

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger alreadyUsed = new AtomicInteger();
        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    couponService.occupy(occupyRequest(userCouponId, ORDER_ID));
                    success.incrementAndGet();
                } catch (BizException e) {
                    if (e.getCode() == ErrorCode.COUPON_ALREADY_USED) {
                        alreadyUsed.incrementAndGet();
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(1, success.get(), "8 线程并发占用只允许一次成功");
        assertEquals(threads - 1, alreadyUsed.get());

        // 已占用后再占用被拒
        BizException reused = assertThrows(BizException.class,
                () -> couponService.occupy(occupyRequest(userCouponId, "O14002")));
        assertEquals(ErrorCode.COUPON_ALREADY_USED, reused.getCode());

        // 释放后回到 UNUSED，可再次占用；释放幂等
        couponService.release(ORDER_ID);
        couponService.release(ORDER_ID);
        UserCouponResponse afterRelease = couponService.detail(userCouponId);
        assertEquals(UserCouponStatus.UNUSED, afterRelease.getStatus());
        assertNotNull(couponService.occupy(occupyRequest(userCouponId, "O14002")));
    }

    @Test
    void expiredCouponIsNotReturnedOnRelease() {
        CouponResponse coupon = createCoupon(CouponType.FIXED, 500L, 0L, 10,
                LocalDateTime.now().minusHours(2), LocalDateTime.now().plusHours(2));
        String userCouponId = couponService.claim(BUYER_ID, coupon.getCouponId()).getUserCouponId();
        couponService.occupy(occupyRequest(userCouponId, ORDER_ID));

        // 占用后券过期：释放不应返还
        jdbcTemplate.update("UPDATE coupon SET end_at = ? WHERE coupon_id = ?",
                LocalDateTime.now().minusMinutes(1), coupon.getCouponId());
        couponService.release(ORDER_ID);

        String stored = jdbcTemplate.queryForObject(
                "SELECT status FROM user_coupon WHERE user_coupon_id = ?",
                String.class, userCouponId);
        assertEquals("USED", stored, "已过期的券释放时不返还");
    }

    @Test
    void refundSuccessReleasesOccupiedCoupon() {
        CouponResponse coupon = createCoupon(CouponType.FIXED, 500L, 0L, 10,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
        String userCouponId = couponService.claim(BUYER_ID, coupon.getCouponId()).getUserCouponId();
        couponService.occupy(occupyRequest(userCouponId, ORDER_ID));

        RefundRequest refundRequest = new RefundRequest();
        refundRequest.setAmount(PAID_AMOUNT);
        refundRequest.setReason("整单退款测试");
        String refundId = refundService.createRefund(BUYER_ID, PAYMENT_ID, refundRequest).getRefundId();

        refundService.auditRefund(refundId, true, SELLER_ID, "同意");

        assertEquals("SUCCESS", jdbcTemplate.queryForObject(
                "SELECT status FROM refund WHERE refund_id = ?", String.class, refundId));
        assertEquals("UNUSED", jdbcTemplate.queryForObject(
                "SELECT status FROM user_coupon WHERE user_coupon_id = ?", String.class, userCouponId));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT order_id FROM user_coupon WHERE user_coupon_id = ?", String.class, userCouponId));
    }

    @Test
    void promotionCreateListUpdateAndActiveFilter() {
        PromotionCreateRequest platform = new PromotionCreateRequest();
        platform.setScope(PromotionScope.PLATFORM);
        platform.setType(PromotionType.FULL_REDUCE);
        platform.setThreshold(10000L);
        platform.setValue(2000L);
        platform.setStackable(true);
        platform.setStartAt(LocalDateTime.now().minusHours(1));
        platform.setEndAt(LocalDateTime.now().plusDays(7));
        PromotionResponse platformSaved = promotionService.adminCreate(ADMIN_ID, platform);
        assertEquals(PromotionStatus.ACTIVE, platformSaved.getStatus());

        PromotionCreateRequest shop = new PromotionCreateRequest();
        shop.setScope(PromotionScope.SHOP);
        shop.setShopId(SELLER_ID);
        shop.setType(PromotionType.FULL_DISCOUNT);
        shop.setThreshold(5000L);
        shop.setValue(85L);
        shop.setStackable(false);
        shop.setStartAt(LocalDateTime.now().minusHours(1));
        shop.setEndAt(LocalDateTime.now().plusDays(7));
        PromotionResponse shopSaved = promotionService.adminCreate(ADMIN_ID, shop);
        assertEquals(SELLER_ID, shopSaved.getShopId());

        // 店铺活动必须指定店铺
        PromotionCreateRequest shopMissing = new PromotionCreateRequest();
        shopMissing.setScope(PromotionScope.SHOP);
        shopMissing.setType(PromotionType.FULL_REDUCE);
        shopMissing.setThreshold(5000L);
        shopMissing.setValue(500L);
        shopMissing.setStackable(true);
        shopMissing.setStartAt(LocalDateTime.now().minusHours(1));
        shopMissing.setEndAt(LocalDateTime.now().plusDays(7));
        BizException missingShop = assertThrows(BizException.class,
                () -> promotionService.adminCreate(ADMIN_ID, shopMissing));
        assertEquals(ErrorCode.PARAM_INVALID, missingShop.getCode());

        // 未生效的活动不出现在生效清单
        PromotionCreateRequest future = new PromotionCreateRequest();
        future.setScope(PromotionScope.PLATFORM);
        future.setType(PromotionType.FULL_REDUCE);
        future.setThreshold(1000L);
        future.setValue(100L);
        future.setStackable(true);
        future.setStartAt(LocalDateTime.now().plusHours(1));
        future.setEndAt(LocalDateTime.now().plusDays(7));
        promotionService.adminCreate(ADMIN_ID, future);

        List<PromotionResponse> active = promotionService.active();
        assertEquals(2, active.size());
        assertTrue(active.stream().anyMatch(p -> p.getPromotionId().equals(platformSaved.getPromotionId())));
        assertTrue(active.stream().anyMatch(p -> p.getPromotionId().equals(shopSaved.getPromotionId())));

        // 停用后不再生效
        PromotionUpdateRequest update = new PromotionUpdateRequest();
        update.setStatus(PromotionStatus.INACTIVE);
        PromotionResponse updated = promotionService.adminUpdate(ADMIN_ID, platformSaved.getPromotionId(), update);
        assertEquals(PromotionStatus.INACTIVE, updated.getStatus());
        List<PromotionResponse> after = promotionService.active();
        assertEquals(1, after.size());

        // 分页列表
        PageResult<PromotionResponse> page = promotionService.adminList(null, 1, 10);
        assertEquals(3, page.getTotal());
        PageResult<PromotionResponse> inactive = promotionService.adminList("INACTIVE", 1, 10);
        assertEquals(1, inactive.getTotal());
    }


    private OccupyCouponRequest occupyRequest(String userCouponId, String orderId) {
        OccupyCouponRequest request = new OccupyCouponRequest();
        request.setUserCouponId(userCouponId);
        request.setOrderId(orderId);
        request.setBuyerId(BUYER_ID);
        return request;
    }

    private CouponResponse createCoupon(CouponType type, long value, long minAmount, int totalQty,
                                        LocalDateTime startAt, LocalDateTime endAt) {
        CouponCreateRequest request = new CouponCreateRequest();
        request.setShopId(SELLER_ID);
        request.setName("测试券");
        request.setType(type);
        request.setValue(value);
        request.setMinAmount(minAmount);
        request.setTotalQty(totalQty);
        request.setStartAt(startAt);
        request.setEndAt(endAt);
        return couponService.adminCreate(ADMIN_ID, request);
    }

    private void insertPaidPayment() {
        jdbcTemplate.update("INSERT INTO payment (payment_id, pay_no, order_id, buyer_id, seller_id, "
                        + "channel, amount, refunded_amount, status, created_at, updated_at, is_deleted) "
                        + "VALUES (?, ?, ?, ?, ?, 'MOCK', ?, 0, 'SUCCESS', ?, ?, 0)",
                PAYMENT_ID, PAY_NO, ORDER_ID, BUYER_ID, SELLER_ID, PAID_AMOUNT,
                LocalDateTime.now().minusMinutes(5), LocalDateTime.now().minusMinutes(5));
    }
}
