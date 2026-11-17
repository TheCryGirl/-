package com.mall.order.service.pricing;

import org.springframework.stereotype.Component;

/**
 * 优惠券抵扣的占位实现（#136）：券在 payment-service（#140），这里先返回 0。
 *
 * <p>与满减一样，这个 bean 保证链的第三步始终存在；#140 接入时按店铺查券并占用即可。
 */
@Component
public class NoopCouponDiscount implements DiscountStep {

    @Override
    public int order() {
        return 3;
    }

    @Override
    public String name() {
        return "优惠券抵扣（待 #140 接入券表）";
    }

    @Override
    public long discountFor(String sellerId, long goodsAmount) {
        return 0L;
    }
}
