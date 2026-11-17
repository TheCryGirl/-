package com.mall.order.service;

import com.mall.order.client.FreightClient;
import com.mall.order.client.dto.FreightConfigDTO;
import com.mall.order.common.Result;
import com.mall.order.service.pricing.DiscountStep;
import com.mall.order.service.pricing.FreightCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单计价链（#136）：商品小计 → 店铺/平台满减、满折 → 优惠券 → 加运费 → 应付合计。
 *
 * <p>预览与下单共用同一个入口，保证「预览显示多少、下单就收多少」。金额全部是「分」的整数，
 * 按比例的折扣只在 {@link com.mall.order.service.pricing.Money#applyRate} 里取整一次。
 *
 * <p>运费：按店铺查 user-service 的运费模板，再按收货省份与店铺商品小计计算；
 * 未配置模板的店铺运费为 0；模板服务不可用时记 warn 并按 0 处理，不阻塞下单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPricingService {

    private final FreightClient freightClient;
    private final List<DiscountStep> discountSteps;

    /** 一家店参与计价的小计。 */
    public record ShopGoods(String sellerId, long goodsAmount) {
    }

    /** 一家店的计价结果。 */
    public record ShopPricing(String sellerId, long goodsAmount, long freightAmount,
                              long discountAmount, long payAmount) {
    }

    /** 整单计价结果：payAmount = goodsAmount - discountAmount + freightAmount。 */
    public record PricingResult(long goodsAmount, long freightAmount, long discountAmount,
                                long payAmount, List<ShopPricing> shops) {
    }

    /**
     * @param shops  按店铺分组的商品小计
     * @param region 收货地区，取收货地址的省份；为空时只匹配 DEFAULT 规则
     */
    public PricingResult price(List<ShopGoods> shops, String region) {
        List<DiscountStep> steps = sortedSteps();
        Map<String, FreightConfigDTO> freightCache = new HashMap<>();
        List<ShopPricing> priced = new ArrayList<>(shops.size());
        long goodsTotal = 0L;
        long freightTotal = 0L;
        long discountTotal = 0L;

        for (ShopGoods shop : shops) {
            long goods = Math.max(0L, shop.goodsAmount());
            long discount = discounts(steps, shop.sellerId(), goods);
            long freight = freight(freightCache, shop.sellerId(), goods, region);
            long pay = goods - discount + freight;
            priced.add(new ShopPricing(shop.sellerId(), goods, freight, discount, pay));
            goodsTotal += goods;
            freightTotal += freight;
            discountTotal += discount;
        }
        long payAmount = goodsTotal - discountTotal + freightTotal;
        return new PricingResult(goodsTotal, freightTotal, discountTotal, payAmount, priced);
    }

    /** 按 order() 固定链顺序执行，并对每家店的抵扣做上下限保护（0 ≤ 抵扣 ≤ 商品小计）。 */
    private long discounts(List<DiscountStep> steps, String sellerId, long goodsAmount) {
        if (goodsAmount <= 0 || steps.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (DiscountStep step : steps) {
            long value;
            try {
                value = step.discountFor(sellerId, goodsAmount);
            } catch (RuntimeException e) {
                log.warn("优惠计算失败，按 0 处理, step={}, sellerId={}", step.name(), sellerId, e);
                continue;
            }
            total += Math.max(0L, value);
        }
        return Math.min(goodsAmount, total);
    }

    private long freight(Map<String, FreightConfigDTO> cache, String sellerId, long goodsAmount, String region) {
        FreightConfigDTO config = cache.computeIfAbsent(sellerId, this::loadFreightConfig);
        return FreightCalculator.calc(goodsAmount, config, region);
    }

    /** 取运费模板：任何异常都降级为「未配置」，运费按 0 计。 */
    private FreightConfigDTO loadFreightConfig(String sellerId) {
        try {
            Result<FreightConfigDTO> result = freightClient.bySeller(sellerId);
            if (result == null || result.getCode() != 0) {
                log.warn("查询运费模板失败，运费按 0 处理, sellerId={}", sellerId);
                return null;
            }
            return result.getData();
        } catch (RuntimeException e) {
            log.warn("查询运费模板异常，运费按 0 处理, sellerId={}", sellerId, e);
            return null;
        }
    }

    private List<DiscountStep> sortedSteps() {
        return discountSteps.stream()
                .sorted(Comparator.comparingInt(DiscountStep::order))
                .toList();
    }
}
