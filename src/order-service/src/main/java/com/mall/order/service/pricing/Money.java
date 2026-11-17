package com.mall.order.service.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额换算（#136）：全链路「分」为整数，唯一允许的四舍五入入口在这里。
 *
 * <p>满折这类按比例计算的优惠只在这里取整一次，后续加减都是整数，因此不会出现二次舍入。
 */
public final class Money {

    private Money() {
    }

    /**
     * 按比例计算金额并四舍五入到分。
     *
     * @param amount 单位分
     * @param rate   比例，例如 0.85 表示八五折
     * @return 单位分，向下限 0
     */
    public static long applyRate(long amount, BigDecimal rate) {
        if (amount <= 0 || rate == null || rate.signum() <= 0) {
            return 0L;
        }
        BigDecimal result = BigDecimal.valueOf(amount)
                .multiply(rate)
                .setScale(0, RoundingMode.HALF_UP);
        return Math.max(0L, result.longValueExact());
    }

    /** 折扣金额 = 原价 × (1 - 比例)，同样只取整一次。 */
    public static long discountByRate(long amount, BigDecimal rate) {
        if (amount <= 0 || rate == null || rate.signum() <= 0) {
            return 0L;
        }
        BigDecimal discount = BigDecimal.valueOf(amount)
                .multiply(BigDecimal.ONE.subtract(rate))
                .setScale(0, RoundingMode.HALF_UP);
        return Math.max(0L, Math.min(amount, discount.longValueExact()));
    }
}
