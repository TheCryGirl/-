package com.mall.order.service.pricing;

import com.mall.order.client.dto.PromotionDTO;
import com.mall.order.client.dto.UserCouponDTO;
import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 优惠券抵扣计价步（#140），链上第 3 步。
 *
 * <p>只做试算不占用（占用在下单时由 order-service 调 payment-service 完成）。
 * 券只对本店生效：券的 shopId 与当前店不匹配时返回 0；商品小计未达门槛抛 60012；
 * 满减/满折步命中且活动 stackable=false 时抛 60014，拒绝叠加。
 * 券值语义与《05-接口契约》一致：FIXED=面额（分），PERCENT=折扣百分比整数（85=8.5 折）。
 */
@Component
public class CouponDiscountStep implements DiscountStep {

    private static final String TYPE_FIXED = "FIXED";
    private static final String TYPE_PERCENT = "PERCENT";

    @Override
    public int order() {
        return 3;
    }

    @Override
    public String name() {
        return "优惠券抵扣";
    }

    @Override
    public long discountFor(PricingContext ctx) {
        long goods = Math.max(0L, ctx.goodsAmount());
        UserCouponDTO coupon = ctx.coupon();
        if (goods <= 0 || coupon == null || ctx.userCouponId() == null) {
            return 0L;
        }
        if (!ctx.sellerId().equals(coupon.getShopId())) {
            return 0L;
        }
        long minAmount = value(coupon.getMinAmount());
        if (goods < minAmount) {
            throw new BizException(ErrorCode.COUPON_MIN_NOT_MET,
                    "未达到优惠券使用门槛，还差 " + (minAmount - goods) + " 分");
        }
        PromotionDTO promotion = ctx.appliedPromotion();
        if (promotion != null && Boolean.FALSE.equals(promotion.getStackable())) {
            throw new BizException(ErrorCode.PROMOTION_CONFLICT, "该活动不可与优惠券叠加使用");
        }
        if (TYPE_PERCENT.equals(coupon.getType())) {
            return Money.discountByRate(goods, percentRate(coupon.getValue()));
        }
        return Math.min(goods, value(coupon.getValue()));
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
