package com.mall.order.service.pricing;

import com.mall.order.client.dto.PromotionDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * 满减 / 满折计价步（#140），链上第 2 步。
 *
 * <p>店铺活动只命中本店，平台活动（scope=PLATFORM）对所有店生效；门槛按该店商品小计判断。
 * 同一店同时命中多条活动时取优惠金额最大的一条（只叠加一次，不重复打折），
 * 并把命中的活动写入 {@link PricingContext#setAppliedPromotion} 供优惠券步做叠加校验。
 * 满折按比例计算，走 {@link Money#discountByRate} 保证四舍五入只发生一次。
 */
@Component
public class PromotionDiscountStep implements DiscountStep {

    private static final String SCOPE_PLATFORM = "PLATFORM";
    private static final String SCOPE_SHOP = "SHOP";
    private static final String TYPE_FULL_REDUCE = "FULL_REDUCE";
    private static final String TYPE_FULL_DISCOUNT = "FULL_DISCOUNT";

    @Override
    public int order() {
        return 2;
    }

    @Override
    public String name() {
        return "店铺/平台满减、满折";
    }

    @Override
    public long discountFor(PricingContext ctx) {
        long goods = Math.max(0L, ctx.goodsAmount());
        if (goods <= 0 || ctx.promotions() == null || ctx.promotions().isEmpty()) {
            return 0L;
        }
        PromotionDTO best = bestApplicable(ctx);
        ctx.setAppliedPromotion(best);
        if (best == null) {
            return 0L;
        }
        if (TYPE_FULL_DISCOUNT.equals(best.getType())) {
            return Money.discountByRate(goods, percentRate(best.getValue()));
        }
        return Math.min(goods, value(best.getValue()));
    }

    /** 命中的活动里取优惠最大的一条；都不满足门槛返回 null */
    private PromotionDTO bestApplicable(PricingContext ctx) {
        List<PromotionDTO> matched = ctx.promotions().stream()
                .filter(promotion -> appliesTo(ctx.sellerId(), promotion))
                .filter(promotion -> value(promotion.getThreshold()) <= ctx.goodsAmount())
                .toList();
        return matched.stream()
                .max(Comparator.comparingLong(promotion -> discountOf(ctx.goodsAmount(), promotion)))
                .orElse(null);
    }

    private boolean appliesTo(String sellerId, PromotionDTO promotion) {
        if (promotion == null) {
            return false;
        }
        if (SCOPE_PLATFORM.equals(promotion.getScope())) {
            return true;
        }
        return SCOPE_SHOP.equals(promotion.getScope()) && sellerId.equals(promotion.getShopId());
    }

    private long discountOf(long goods, PromotionDTO promotion) {
        if (TYPE_FULL_DISCOUNT.equals(promotion.getType())) {
            return Money.discountByRate(goods, percentRate(promotion.getValue()));
        }
        return Math.min(goods, value(promotion.getValue()));
    }

    /** 折扣百分比整数（85 = 8.5 折）转比例；非法值按 0 折处理（不给优惠） */
    private static BigDecimal percentRate(Long percent) {
        long safe = value(percent);
        if (safe <= 0 || safe >= 100) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(safe, 2);
    }

    private static long value(Long raw) {
        return raw == null ? 0L : Math.max(0L, raw);
    }
}
