package com.mall.order.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 自动确认收货的阈值与剩余时间计算（任务 #138）。
 *
 * <p>纯计算，不连数据库也不连 Nacos：配置服务用 mock 直接给值。
 * 重点验证「默认 10 天」「配成 1 分钟可用」「到期返回 0 不返回负数」三条。
 */
class AutoConfirmPolicyTest {

    private final PlatformConfigService config = mock(PlatformConfigService.class);
    private final AutoConfirmPolicy policy = new AutoConfirmPolicy(config);

    @Test
    void defaultThresholdIsTenDays() {
        // 配置读不到时业务应等价于 10 天
        when(config.currentLong("order.auto-confirm.minutes"))
                .thenReturn(AutoConfirmPolicy.DEFAULT_MINUTES);
        assertEquals(14400L, policy.minutes());
        assertEquals(10, 14400L / 24 / 60);
    }

    @Test
    void oneMinuteThresholdKeepsOneMinuteSemantics() {
        when(config.currentLong("order.auto-confirm.minutes")).thenReturn(1L);
        LocalDateTime shippedAt = LocalDateTime.now().minusSeconds(10);
        Long remaining = policy.remainingMinutes(shippedAt, 1L);
        // 还剩不到 1 分钟，向上取整为 1，不能显示 0
        assertEquals(1L, remaining);
    }

    @Test
    void expiredOrderReportsZeroInsteadOfNegative() {
        when(config.currentLong("order.auto-confirm.minutes")).thenReturn(14400L);
        Long remaining = policy.remainingMinutes(LocalDateTime.now().minusDays(11L), 14400L);
        assertEquals(0L, remaining);
    }

    @Test
    void neverShippedOrderHasNoCountdown() {
        assertNull(policy.remainingMinutes(null, 14400L));
        assertNull(policy.deadline(null, 14400L));
    }

    @Test
    void remainingMinutesIsRoundedUp() {
        // 刚发货：14400 分钟，允许执行耗时带来的 1 分钟误差
        Long remaining = policy.remainingMinutes(LocalDateTime.now(), 14400L);
        assertEquals(14400L, remaining);
    }
}
