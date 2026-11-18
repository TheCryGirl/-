package com.mall.order.service;

import com.mall.order.client.FreightClient;
import com.mall.order.client.PromotionClient;
import com.mall.order.client.dto.FreightConfigDTO;
import com.mall.order.client.dto.PromotionDTO;
import com.mall.order.client.dto.UserCouponDTO;
import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import com.mall.order.common.Result;
import com.mall.order.service.pricing.CouponDiscountStep;
import com.mall.order.service.pricing.PromotionDiscountStep;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #140 促销计价链：满减/满折与优惠券的试算、叠加约束与错误上抛。
 */
class Issue140PromotionPricingTest {

    private static final String SELLER_A = "S-A";
    private static final String SELLER_B = "S-B";
    private static final String BUYER = "U-140";

    @Test
    void fullReduceAppliesOnlyWhenThresholdMet() {
        PromotionDTO reduce = promotion("P1", "PLATFORM", null, "FULL_REDUCE", 10000L, 2000L, true);
        OrderPricingService service = service(reduce);

        OrderPricingService.PricingResult below = service.price(
                List.of(new OrderPricingService.ShopGoods(SELLER_A, 9999L)), null, BUYER, null);
        assertThat(below.discountAmount()).isZero();

        OrderPricingService.PricingResult met = service.price(
                List.of(new OrderPricingService.ShopGoods(SELLER_A, 10000L)), null, BUYER, null);
        assertThat(met.discountAmount()).isEqualTo(2000L);
        assertThat(met.payAmount()).isEqualTo(8000L);
    }

    @Test
    void shopPromotionOnlyHitsItsShopWhilePlatformHitsAll() {
        PromotionDTO shopPromo = promotion("P2", "SHOP", SELLER_A, "FULL_REDUCE", 100L, 50L, true);
        PromotionDTO platformPromo = promotion("P3", "PLATFORM", null, "FULL_REDUCE", 500L, 30L, true);
        OrderPricingService service = service(shopPromo, platformPromo);

        OrderPricingService.PricingResult result = service.price(List.of(
                new OrderPricingService.ShopGoods(SELLER_A, 1000L),
                new OrderPricingService.ShopGoods(SELLER_B, 1000L)), null, BUYER, null);

        // A 店同时命中店铺满减 50 与平台满减 30，只取最大一条；B 店只有平台满减 30
        assertThat(result.shops()).extracting(OrderPricingService.ShopPricing::discountAmount)
                .containsExactly(50L, 30L);
    }

    @Test
    void fullDiscountUsesSingleRoundingAndBestPromotionWins() {
        PromotionDTO weak = promotion("P4", "PLATFORM", null, "FULL_DISCOUNT", 100L, 85L, true);
        PromotionDTO strong = promotion("P5", "PLATFORM", null, "FULL_REDUCE", 100L, 400L, true);
        OrderPricingService service = service(weak, strong);

        OrderPricingService.PricingResult result = service.price(
                List.of(new OrderPricingService.ShopGoods(SELLER_A, 10000L)), null, BUYER, null);
        // 满折 10000*0.15=1500 < 满减 400，取最大的一条，且只叠加一次
        assertThat(result.discountAmount()).isEqualTo(1500L);
    }

    @Test
    void fixedCouponKeepsOneCentPayableAndShopMismatchGivesZero() {
        UserCouponDTO coupon = coupon(SELLER_A, "FIXED", 500L, 0L);
        OrderPricingService service = service();

        OrderPricingService.PricingResult ownShop = service.price(
                List.of(new OrderPricingService.ShopGoods(SELLER_A, 400L)), null, BUYER, coupon);
        assertThat(ownShop.discountAmount()).isEqualTo(399L);
        assertThat(ownShop.payAmount()).isEqualTo(1L);

        // 券属于 A 店，对 B 店不生效
        OrderPricingService.PricingResult otherShop = service.price(
                List.of(new OrderPricingService.ShopGoods(SELLER_B, 4000L)), null, BUYER, coupon);
        assertThat(otherShop.discountAmount()).isZero();
    }

    @Test
    void percentCouponDiscountsByRate() {
        UserCouponDTO coupon = coupon(SELLER_A, "PERCENT", 85L, 0L);
        OrderPricingService service = service();

        OrderPricingService.PricingResult result = service.price(
                List.of(new OrderPricingService.ShopGoods(SELLER_A, 10000L)), null, BUYER, coupon);
        assertThat(result.discountAmount()).isEqualTo(1500L);
    }

    @Test
    void couponBelowMinAmountThrows60012AndPropagates() {
        UserCouponDTO coupon = coupon(SELLER_A, "FIXED", 500L, 5000L);
        OrderPricingService service = service();

        assertThatThrownBy(() -> service.price(
                List.of(new OrderPricingService.ShopGoods(SELLER_A, 4999L)), null, BUYER, coupon))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.COUPON_MIN_NOT_MET);
    }

    @Test
    void nonStackablePromotionConflictsWithCoupon() {
        PromotionDTO promo = promotion("P6", "SHOP", SELLER_A, "FULL_REDUCE", 1000L, 100L, false);
        UserCouponDTO coupon = coupon(SELLER_A, "FIXED", 500L, 0L);
        OrderPricingService service = service(promo);

        assertThatThrownBy(() -> service.price(
                List.of(new OrderPricingService.ShopGoods(SELLER_A, 5000L)), null, BUYER, coupon))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.PROMOTION_CONFLICT);
    }

    @Test
    void stackablePromotionAndCouponBothApply() {
        PromotionDTO promo = promotion("P7", "SHOP", SELLER_A, "FULL_REDUCE", 1000L, 100L, true);
        UserCouponDTO coupon = coupon(SELLER_A, "FIXED", 500L, 0L);
        OrderPricingService service = service(promo);

        OrderPricingService.PricingResult result = service.price(
                List.of(new OrderPricingService.ShopGoods(SELLER_A, 5000L)), null, BUYER, coupon);
        assertThat(result.discountAmount()).isEqualTo(600L);
        assertThat(result.payAmount()).isEqualTo(4400L);
    }

    @Test
    void promotionServiceFailureDegradesToNoPromotion() {
        PromotionClient promotionClient = mock(PromotionClient.class);
        when(promotionClient.active(any())).thenThrow(new RuntimeException("payment-service down"));
        OrderPricingService service = new OrderPricingService(mock(FreightClient.class), promotionClient,
                List.of(new PromotionDiscountStep(), new CouponDiscountStep()));

        OrderPricingService.PricingResult result = service.price(
                List.of(new OrderPricingService.ShopGoods(SELLER_A, 5000L)), null, BUYER, null);
        assertThat(result.discountAmount()).isZero();
        assertThat(result.payAmount()).isEqualTo(5000L);
    }

    private static OrderPricingService service(PromotionDTO... promotions) {
        FreightClient freightClient = mock(FreightClient.class);
        when(freightClient.bySeller(anyString())).thenReturn(Result.success(freeFreight()));
        PromotionClient promotionClient = mock(PromotionClient.class);
        when(promotionClient.active(any())).thenReturn(Result.success(List.of(promotions)));
        return new OrderPricingService(freightClient, promotionClient,
                List.of(new PromotionDiscountStep(), new CouponDiscountStep()));
    }

    private static PromotionDTO promotion(String id, String scope, String shopId, String type,
                                          long threshold, long value, boolean stackable) {
        PromotionDTO dto = new PromotionDTO();
        dto.setPromotionId(id);
        dto.setScope(scope);
        dto.setShopId(shopId);
        dto.setType(type);
        dto.setThreshold(threshold);
        dto.setValue(value);
        dto.setStackable(stackable);
        dto.setStartAt(LocalDateTime.now().minusHours(1));
        dto.setEndAt(LocalDateTime.now().plusDays(7));
        dto.setStatus("ACTIVE");
        return dto;
    }

    private static UserCouponDTO coupon(String shopId, String type, long value, long minAmount) {
        UserCouponDTO dto = new UserCouponDTO();
        dto.setUserCouponId("UC-140-1");
        dto.setCouponId("C-140-1");
        dto.setShopId(shopId);
        dto.setUserId(BUYER);
        dto.setName("测试券");
        dto.setType(type);
        dto.setValue(value);
        dto.setMinAmount(minAmount);
        dto.setStatus("UNUSED");
        dto.setStartAt(LocalDateTime.now().minusHours(1));
        dto.setEndAt(LocalDateTime.now().plusDays(7));
        return dto;
    }

    private static FreightConfigDTO freeFreight() {
        FreightConfigDTO config = new FreightConfigDTO();
        config.setTemplateId("FT-FREE");
        config.setFreeThreshold(0L);
        FreightConfigDTO.FreightRuleDTO rule = new FreightConfigDTO.FreightRuleDTO();
        rule.setRegionCode("DEFAULT");
        rule.setMinAmount(0L);
        rule.setFee(0L);
        config.setRules(List.of(rule));
        return config;
    }
}
