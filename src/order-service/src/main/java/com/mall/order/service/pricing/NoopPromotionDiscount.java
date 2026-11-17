package com.mall.order.service.pricing;

import org.springframework.stereotype.Component;

/**
 * 满减 / 满折的占位实现（#136）：规则表 {@code promotion} 由 #140 建，这里先返回 0。
 *
 * <p>保留这个 bean 是为了让计价链的第二步始终存在、顺序稳定；
 * #140 接入时把本类替换成读 promotion 表的实现即可，链本身不用改。
 */
@Component
public class NoopPromotionDiscount implements DiscountStep {

    @Override
    public int order() {
        return 2;
    }

    @Override
    public String name() {
        return "店铺/平台满减、满折（待 #140 接入规则表）";
    }

    @Override
    public long discountFor(String sellerId, long goodsAmount) {
        return 0L;
    }
}
