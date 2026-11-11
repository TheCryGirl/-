package com.mall.payment.service;

import com.mall.payment.client.OrderClient;
import com.mall.payment.client.ProductClient;
import com.mall.payment.client.dto.StockConfirmRequest;
import com.mall.payment.common.Result;
import com.mall.payment.entity.RefundNotificationTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款通知任务的投递。
 *
 * <p>两条路径：
 * <ul>
 *   <li>{@link #dispatchAfterCommit}：退款审核事务提交后立即投递，保持原有交互（审核成功即通知）；</li>
 *   <li>{@link #dispatchDue}：定时扫描到期任务，覆盖「提交后投递失败」「进程在提交与投递之间退出」
 *       「下游长时间不可用」等情况。这是唯一的兜底，不依赖日志与人工补偿。</li>
 * </ul>
 *
 * <p>几条硬约束：
 * <ul>
 *   <li>不持有数据库事务做远程调用：本类没有任何 {@code @Transactional}；抢占、完成、失败
 *       都交给 {@link RefundNotificationTxService} 的 REQUIRES_NEW 方法独立提交。</li>
 *   <li>每个任务独立完成：一条成功一条失败时，只有失败的那条留在 PENDING 等待重试。</li>
 *   <li>并发安全：多实例用同一份条件 UPDATE 抢占，抢不到就跳过；PROCESSING 租约过期后可被回收。</li>
 *   <li>至少一次：下游 order/product 侧本身幂等（订单状态机、预占状态机），重复投递不会重复生效。</li>
 * </ul>
 */
@Slf4j
@Component
public class RefundNotificationDispatcher {

    /** 失败退避基准，秒。 */
    private final long baseBackoffSeconds;

    /** 退避上限，秒。到达上限后按固定间隔继续重试，永不丢弃任务。 */
    private static final long MAX_BACKOFF_SECONDS = 600L;

    /** 处理中租约，秒：实例崩溃后最迟这么久任务可被其他实例回收。 */
    private static final long LEASE_SECONDS = 120L;

    /** last_error 列长度。 */
    private static final int LAST_ERROR_MAX_LENGTH = 255;

    private final RefundNotificationTxService notificationTxService;
    private final OrderClient orderClient;
    private final ProductClient productClient;

    /** 定时扫描单次最多处理的任务数。 */
    private final int batchSize;

    /** 定时扫描开关；测试环境关闭，避免后台线程与用例的 Mockito 调用计数互相干扰。 */
    private final boolean enabled;

    public RefundNotificationDispatcher(RefundNotificationTxService notificationTxService,
                                        OrderClient orderClient,
                                        ProductClient productClient,
                                        @Value("${payment.refund-notification.base-backoff-seconds:30}")
                                        long baseBackoffSeconds,
                                        @Value("${payment.refund-notification.batch-size:50}")
                                        int batchSize,
                                        @Value("${payment.refund-notification.enabled:true}")
                                        boolean enabled) {
        this.notificationTxService = notificationTxService;
        this.orderClient = orderClient;
        this.productClient = productClient;
        this.baseBackoffSeconds = Math.min(MAX_BACKOFF_SECONDS, Math.max(1, baseBackoffSeconds));
        this.batchSize = Math.max(1, batchSize);
        this.enabled = enabled;
    }

    /**
     * 退款审核提交后立即投递该订单的待办任务。
     *
     * <p>调用方保证在 afterCommit 之后调用；本方法自身异常不外抛，失败靠定时扫描兜底。
     */
    public void dispatchAfterCommit(String orderId) {
        try {
            List<RefundNotificationTask> tasks = notificationTxService.findPending(
                    notificationTxService.findByOrderId(orderId));
            for (RefundNotificationTask task : tasks) {
                tryDeliver(task);
            }
        } catch (Exception e) {
            log.error("退款通知立即投递异常，留待定时重试, orderId={}", orderId, e);
        }
    }

    /**
     * 定时扫描到期任务。关闭时（payment.refund-notification.enabled=false）不执行。
     */
    @Scheduled(fixedDelayString = "${payment.refund-notification.interval-ms:60000}")
    public void scheduledDispatch() {
        if (!enabled) {
            return;
        }
        dispatchDue();
    }

    /**
     * 扫描并投递一批到期任务。每批最多 {@code payment.refund-notification.batch-size}（默认 50）条。
     */
    public void dispatchDue() {
        List<RefundNotificationTask> due;
        try {
            due = notificationTxService.selectDue(LocalDateTime.now(), batchSize);
        } catch (Exception e) {
            log.error("查询退款通知任务失败", e);
            return;
        }
        if (due == null || due.isEmpty()) {
            return;
        }
        log.info("开始重试退款通知任务, 待处理={}", due.size());
        for (RefundNotificationTask task : due) {
            tryDeliver(task);
        }
    }

    /**
     * 抢占一条任务并投递。已被其他实例抢到（claim 返回 null）时直接跳过，不重复调用下游。
     */
    private void tryDeliver(RefundNotificationTask task) {
        RefundNotificationTask claimed;
        try {
            claimed = notificationTxService.claim(task.getOrderId(), task.getEventType(),
                    LocalDateTime.now().plusSeconds(LEASE_SECONDS));
        } catch (Exception e) {
            log.error("抢占退款通知任务失败, orderId={}, eventType={}",
                    task.getOrderId(), task.getEventType(), e);
            return;
        }
        if (claimed == null) {
            return;
        }

        String error = null;
        try {
            Result<Void> result = invoke(claimed);
            if (result == null) {
                error = "下游返回为空";
            } else if (result.getCode() != 0) {
                error = "Downstream rejected: code=" + result.getCode();
            }
        } catch (Exception e) {
            // 只取可读报错，不落异常类名与堆栈
            error = "Downstream call failed";
        }

        if (error == null) {
            try {
                if (notificationTxService.markDone(claimed) == 0) return;
                log.info("退款通知投递成功, orderId={}, eventType={}, attempts={}",
                        claimed.getOrderId(), claimed.getEventType(), claimed.getAttempts());
            } catch (Exception e) {
                // 任务已 PROCESSING 且 lease 未过期，下一轮会被租约回收后重投；下游幂等
                log.error("标记退款通知任务完成失败, orderId={}, eventType={}",
                        claimed.getOrderId(), claimed.getEventType(), e);
            }
            return;
        }

        LocalDateTime nextRetryAt = LocalDateTime.now()
                .plusSeconds(backoffSeconds(claimed.getAttempts()));
        try {
            notificationTxService.markFailed(claimed, sanitize(error), nextRetryAt);
            log.warn("退款通知投递失败，已排入重试, orderId={}, eventType={}, attempts={}, nextRetryAt={}",
                    claimed.getOrderId(), claimed.getEventType(), claimed.getAttempts(), nextRetryAt);
        } catch (Exception e) {
            // 保持 PROCESSING 直到租约过期，任务仍在库里，不会被静默丢弃
            log.error("记录退款通知失败状态失败, orderId={}, eventType={}",
                    claimed.getOrderId(), claimed.getEventType(), e);
        }
    }

    private Result<Void> invoke(RefundNotificationTask task) {
        if (RefundNotificationTxService.EVENT_ORDER_REFUNDED.equals(task.getEventType())) {
            return orderClient.markRefunded(task.getOrderId());
        }
        if (RefundNotificationTxService.EVENT_STOCK_RESTOCK.equals(task.getEventType())) {
            return productClient.refundRestock(new StockConfirmRequest(task.getOrderId()));
        }
        throw new IllegalStateException("未知的通知事件类型: " + task.getEventType());
    }

    /**
     * 指数退避，封顶 {@link #MAX_BACKOFF_SECONDS}：第 n 次失败后等 base * 2^(n-1) 秒。
     */
    long backoffSeconds(int attempts) {
        int exponent = Math.max(attempts - 1, 0);
        // 2^13 已远超上限，避免移位溢出
        if (exponent > 13) {
            return MAX_BACKOFF_SECONDS;
        }
        long delay = baseBackoffSeconds * (1L << exponent);
        return Math.min(delay, MAX_BACKOFF_SECONDS);
    }

    /**
     * 截断到 last_error 列长度（255）。不写凭据：这里放的是下游返回码/报错文本，
     * 不含我方令牌，也不含异常类名与堆栈。
     */
    private static String sanitize(String error) {
        String text = error == null ? "未知错误" : error;
        text = text.replaceAll("(?i)(authorization|token|secret|password)\\s*[:=]\\s*\\S+", "$1=***");
        return text.length() > LAST_ERROR_MAX_LENGTH ? text.substring(0, LAST_ERROR_MAX_LENGTH) : text;
    }
}
