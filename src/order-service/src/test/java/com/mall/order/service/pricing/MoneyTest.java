package com.mall.order.service.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 金额换算（#136）：四舍五入只发生一次，且都在 {@link Money} 里。
 */
class MoneyTest {

    @Test
    void rateIsRoundedHalfUpExactlyOnce() {
        // 999 × 0.85 = 849.15 → 849（只取整一次）
        assertThat(Money.applyRate(999L, new BigDecimal("0.85"))).isEqualTo(849L);
        // 10000 × 0.9 = 9000
        assertThat(Money.applyRate(10000L, new BigDecimal("0.9"))).isEqualTo(9000L);
        // 333 × 0.333 = 110.889 → 111
        assertThat(Money.applyRate(333L, new BigDecimal("0.333"))).isEqualTo(111L);
    }

    @Test
    void discountByRateNeverExceedsAmount() {
        assertThat(Money.discountByRate(10000L, new BigDecimal("0.9"))).isEqualTo(1000L);
        assertThat(Money.discountByRate(100L, new BigDecimal("0.001"))).isEqualTo(100L);
    }

    @Test
    void invalidInputsProduceZeroRatherThanNegativeAmount() {
        assertThat(Money.applyRate(0L, new BigDecimal("0.5"))).isZero();
        assertThat(Money.applyRate(-100L, new BigDecimal("0.5"))).isZero();
        assertThat(Money.applyRate(100L, null)).isZero();
        assertThat(Money.applyRate(100L, BigDecimal.ZERO)).isZero();
        assertThat(Money.discountByRate(100L, null)).isZero();
    }
}
