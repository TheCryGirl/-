package com.mall.order.service.pricing;

import com.mall.order.client.dto.PromotionDTO;
import com.mall.order.client.dto.UserCouponDTO;

import java.util.List;

/**
 * 计价链单店上下文（#140）。
 *
 * <p>{@link DiscountStep#discountFor(PricingContext)} 的入参：链内共享选中券与生效活动，
 * 各步只做试算不占用。{@code appliedPromotion} 是链内共享槽位：满减/满折步把命中的活动
 * 写入，优惠券步据此校验 stackable 叠加约束。
 */
public class PricingContext {

    private final String userId;
    private final String sellerId;
    private final long goodsAmount;
    private final String userCouponId;
    private final UserCouponDTO coupon;
    private final List<PromotionDTO> promotions;

    private PromotionDTO appliedPromotion;

    public PricingContext(String userId, String sellerId, long goodsAmount,
                          String userCouponId, UserCouponDTO coupon, List<PromotionDTO> promotions) {
        this.userId = userId;
        this.sellerId = sellerId;
        this.goodsAmount = goodsAmount;
        this.userCouponId = userCouponId;
        this.coupon = coupon;
        this.promotions = promotions;
    }

    public String userId() {
        return userId;
    }

    public String sellerId() {
        return sellerId;
    }

    public long goodsAmount() {
        return goodsAmount;
    }

    /** 选中的用户券ID；未用券为 null */
    public String userCouponId() {
        return userCouponId;
    }

    /** 选中的券信息；未用券为 null */
    public UserCouponDTO coupon() {
        return coupon;
    }

    /** 当前生效中的满减/满折活动 */
    public List<PromotionDTO> promotions() {
        return promotions;
    }

    /** 满减/满折步命中的活动（链内共享，供优惠券步叠加校验） */
    public PromotionDTO appliedPromotion() {
        return appliedPromotion;
    }

    public void setAppliedPromotion(PromotionDTO appliedPromotion) {
        this.appliedPromotion = appliedPromotion;
    }
}
