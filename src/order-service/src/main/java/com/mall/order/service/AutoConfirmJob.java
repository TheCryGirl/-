package com.mall.order.service;

import com.mall.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发货超期自动确认收货（任务 #138）。
 *
 * <p>扫描 {@code status='SHIPPED'} 且 {@code shipped_at + N 分钟 < NOW()} 的订单，
 * N 由 {@link AutoConfirmPolicy} 从平台配置读取（默认 14400 分钟 = 10 天），不硬编码。
 *
 * <p>并发安全与超时取消同构：扫描只负责找候选，真正改状态是
 * {@link OrderService#autoConfirm} 里的条件更新
 * （{@code UPDATE ... WHERE status='SHIPPED'}）。
 * 两个实例同时扫到同一单时，后到的那条影响行数为 0，直接跳过——
 * 不需要分布式锁，也不需要 Redis。
 *
 * <p>轨迹出现 SIGNED 也**不**触发确认：签收是快递侧的事实，
 * 确认收货是订单侧的动作，两者由本任务或买家手动点击统一收口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoConfirmJob {

    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final AutoConfirmPolicy autoConfirmPolicy;

    @Value("${order.jobs.enabled:true}")
    private boolean jobsEnabled;

    @Value("${order.auto-confirm.batch-size:100}")
    private int batchSize;

    /**
     * 定时入口。
     *
     * <p>{@code order.jobs.enabled=false} 用来在测试与本地调试里关掉调度；
     * 这里做的是「跳过执行」而不是「不注册 bean」，好处是集成测试可以直接调用
     * {@link #scan()} 跑一轮，不必等待真实定时器触发。
     */
    @Scheduled(fixedDelayString = "${order.auto-confirm.scan-interval-ms:60000}",
            initialDelayString = "${order.auto-confirm.initial-delay-ms:45000}")
    public void scheduledScan() {
        if (!jobsEnabled) {
            return;
        }
        scan();
    }

    /** 执行一轮扫描；返回真正完成确认的订单数，便于测试断言 */
    public int scan() {
        long minutes = autoConfirmPolicy.minutes();
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(minutes);
        List<String> candidates = orderMapper.selectAutoConfirmCandidates(deadline, Math.max(1, batchSize));
        int confirmed = 0;
        for (String orderId : candidates) {
            if (orderService.autoConfirm(orderId)) {
                confirmed++;
            }
        }
        if (!candidates.isEmpty()) {
            log.info("自动确认收货扫描完成, minutes={}, candidates={}, confirmed={}",
                    minutes, candidates.size(), confirmed);
        }
        return confirmed;
    }
}
