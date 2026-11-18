package com.mall.order.service;

import com.mall.order.client.FreightClient;
import com.mall.order.client.PromotionClient;
import com.mall.order.client.dto.FreightConfigDTO;
import com.mall.order.common.Result;
import com.mall.order.service.pricing.DiscountStep;
import com.mall.order.service.pricing.PricingContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 计价链（#136）：顺序固定为 商品小计 → 满减/满折 → 优惠券 → 加运费 → 应付合计，
 * 且优惠合计不超过商品小计、运费按店铺分别计算后汇总。
 */
class OrderPricingServiceTest {

    @Test
    void discountStepsRunInChainOrderAndPayFormulaHolds() {
        List<String> executed = new ArrayList<>();
        FreightClient freightClient = mock(FreightClient.class);
        when(freightClient.bySeller(anyString())).thenReturn(Result.success(config(300L)));

        DiscountStep coupon = step(3, "coupon", executed, 200L);
        DiscountStep promotion = step(2, "promotion", executed, 500L);
        OrderPricingService service = service(freightClient, coupon, promotion);

        OrderPricingService.PricingResult result = service.price(
                List.of(new OrderPricingService.ShopGoods("U-SELLER", 10000L)), "天津市");

        assertThat(executed).containsExactly("promotion", "coupon");
        assertThat(result.goodsAmount()).isEqualTo(10000L);
        assertThat(result.discountAmount()).isEqualTo(700L);
        assertThat(result.freightAmount()).isEqualTo(300L);
        assertThat(result.payAmount()).isEqualTo(9600L);
        assertThat(result.payAmount()).isEqualTo(result.goodsAmount() - result.discountAmount()
                + result.freightAmount());
    }

    @Test
    void discountsAreClampedToGoodsAmountPerShop() {
        FreightClient freightClient = mock(FreightClient.class);
        when(freightClient.bySeller(anyString())).thenReturn(Result.success(config(300L)));
        List<String> executed = new ArrayList<>();
        OrderPricingService service = service(freightClient,
                step(2, "promotion", executed, 800L), step(3, "coupon", executed, 500L));

        OrderPricingService.PricingResult result = service.price(
                List.of(new OrderPricingService.ShopGoods("U-SELLER", 1000L)), "天津市");

        assertThat(result.discountAmount()).isEqualTo(1000L);
        assertThat(result.payAmount()).isEqualTo(300L);
    }

    @Test
    void freightIsSummedPerShopAndMissingTemplateCostsNothing() {
        FreightClient freightClient = mock(FreightClient.class);
        when(freightClient.bySeller("U-A")).thenReturn(Result.success(config(600L)));
        when(freightClient.bySeller("U-B")).thenReturn(Result.success(config(0L)));
        OrderPricingService service = service(freightClient);

        OrderPricingService.PricingResult result = service.price(List.of(
                new OrderPricingService.ShopGoods("U-A", 3000L),
                new OrderPricingService.ShopGoods("U-B", 5000L)), "天津市");

        assertThat(result.shops()).extracting(OrderPricingService.ShopPricing::freightAmount)
                .containsExactly(600L, 0L);
        assertThat(result.freightAmount()).isEqualTo(600L);
        assertThat(result.payAmount()).isEqualTo(8600L);
    }

    @Test
    void freightServiceFailureDegradesToZeroInsteadOfBlockingCheckout() {
        FreightClient freightClient = mock(FreightClient.class);
        when(freightClient.bySeller(anyString())).thenThrow(new RuntimeException("user-service down"));
        OrderPricingService service = service(freightClient);

        OrderPricingService.PricingResult result = service.price(
                List.of(new OrderPricingService.ShopGoods("U-A", 5000L)), "天津市");

        assertThat(result.freightAmount()).isZero();
        assertThat(result.payAmount()).isEqualTo(5000L);
    }

    /** 按比例计算的折扣走 Money，保证只取整一次。 */
    @Test
    void percentageDiscountUsesSingleRounding() {
        assertThat(com.mall.order.service.pricing.Money.discountByRate(999L, new BigDecimal("0.85")))
                .isEqualTo(150L);
    }

    private static OrderPricingService service(FreightClient freightClient, DiscountStep... steps) {
        PromotionClient promotionClient = mock(PromotionClient.class);
        when(promotionClient.active(anyString())).thenReturn(Result.success(List.of()));
        return new OrderPricingService(freightClient, promotionClient, List.of(steps));
    }

    private static DiscountStep step(int order, String name, List<String> executed, long discount) {
        return new DiscountStep() {
            @Override
            public int order() {
                return order;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public long discountFor(PricingContext ctx) {
                executed.add(name);
                return discount;
            }
        };
    }

    private static FreightConfigDTO config(long defaultFee) {
        FreightConfigDTO config = new FreightConfigDTO();
        config.setTemplateId("FT-TEST");
        config.setFreeThreshold(0L);
        FreightConfigDTO.FreightRuleDTO rule = new FreightConfigDTO.FreightRuleDTO();
        rule.setRegionCode("DEFAULT");
        rule.setMinAmount(0L);
        rule.setFee(defaultFee);
        config.setRules(List.of(rule));
        return config;
    }
}
