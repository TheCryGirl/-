package com.mall.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 自动确认收货的规则计算（任务 #138）。
 *
 * <p>阈值来自平台配置 {@code order.auto-confirm.minutes}，默认 {@value #DEFAULT_MINUTES}
 * 分钟（= 10 天），不硬编码。单位取「分钟」而不是「天」有两个原因：
 * <ol>
 *   <li>验收要求把 N 配成 1 分钟就能造出到期订单，用天做单位只能填 0.0007，管理端没法用；</li>
 *   <li>剩余时间要显示成「X 天 / X 小时 / X 分钟」，内部按分钟算不必反复换算。</li>
 * </ol>
 * 默认值与 issue 约定的「10 天」完全一致，业务上仍然是按天理解的。
 *
 * <p>单独抽出来是因为「订单详情要显示剩余时间」和「定时任务要算截止点」是同一套口径，
 * 两边各写一遍迟早会算错。这里只做纯计算，不碰数据库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoConfirmPolicy {

    /** 配置缺失或非法时的兜底阈值：10 天 */
    static final long DEFAULT_MINUTES = 10L * 24 * 60;

    private final PlatformConfigService platformConfigService;

    /** 当前生效的阈值（分钟） */
    public long minutes() {
        long minutes = platformConfigService.currentLong("order.auto-confirm.minutes");
        return minutes < 1 ? DEFAULT_MINUTES : minutes;
    }

    /** 自动确认的截止时刻：发货时间 + N 分钟 */
    public LocalDateTime deadline(LocalDateTime shippedAt, long minutes) {
        return shippedAt == null ? null : shippedAt.plusMinutes(minutes);
    }

    /**
     * 剩余可确认分钟数，向上取整；已到期返回 0，不返回负数。
     *
     * <p>向上取整是为了和用户直觉一致：还剩 30 秒时显示「1 分钟」，而不是「0 分钟」。
     */
    public Long remainingMinutes(LocalDateTime shippedAt, long minutes) {
        if (shippedAt == null) {
            return null;
        }
        long seconds = Duration.between(LocalDateTime.now(), deadline(shippedAt, minutes)).getSeconds();
        if (seconds <= 0) {
            return 0L;
        }
        return (seconds + 59) / 60;
    }
}
