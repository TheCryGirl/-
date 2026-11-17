package com.mall.order.service;

import com.mall.order.mapper.MainOrderMapper;
import com.mall.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 待支付超时扫描。状态 CAS 在 OrderService.cancelTimeout 中完成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "order.jobs.enabled", havingValue = "true", matchIfMissing = true)
@RefreshScope
public class OrderTimeoutJob {

    private final OrderMapper orderMapper;
    private final MainOrderMapper mainOrderMapper;
    private final OrderService orderService;

    @Value("${order.timeout.minutes:30}")
    private long timeoutMinutes;

    @Value("${order.timeout.batch-size:100}")
    private int batchSize;

    @Value("${order.timeout.scan-interval-ms:60000}")
    private long scanIntervalMs;

    @Scheduled(fixedDelayString = "${order.timeout.scan-interval-ms:60000}", initialDelayString = "30000")
    public void scan() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(Math.max(1, timeoutMinutes));
        List<String> candidates = orderMapper.selectTimeoutCandidates(deadline, Math.max(1, batchSize));
        int cancelled = 0;
        for (String orderId : candidates) {
            if (orderService.cancelTimeout(orderId)) {
                cancelled++;
            }
        }
        if (!candidates.isEmpty()) {
            log.info("待支付超时扫描完成, candidates={}, cancelled={}", candidates.size(), cancelled);
        }
        // 主订单超时（#135）：跨店合并结算的主订单到期后整体取消，未支付子订单一起释放库存。
        // 子订单与主订单 expire_at 相同，先扫子订单再扫主订单，两者 CAS 互不冲突。
        List<String> mainCandidates = mainOrderMapper.selectTimeoutCandidates(deadline, Math.max(1, batchSize));
        int mainCancelled = 0;
        for (String mainOrderId : mainCandidates) {
            if (orderService.cancelMainTimeout(mainOrderId)) {
                mainCancelled++;
            }
        }
        if (!mainCandidates.isEmpty()) {
            log.info("主订单超时扫描完成, candidates={}, cancelled={}", mainCandidates.size(), mainCancelled);
        }
        orderService.retryUnreleasedInventory();
        orderService.retryCompensations();
    }
}

