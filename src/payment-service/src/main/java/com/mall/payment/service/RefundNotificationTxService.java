package com.mall.payment.service;

import com.mall.payment.entity.RefundNotificationTask;
import com.mall.payment.mapper.RefundNotificationTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款通知任务的本地事务操作。
 *
 * <p>方法分工：
 * <ul>
 *   <li>{@link #createTasks} 由退款审核在同一个业务事务里调用，参与提交/回滚（因此不带自己的事务边界）；</li>
 *   <li>其余读写方法都是 {@code REQUIRES_NEW}：由调度在事务提交后的 afterCommit 回调里调用，
 *       此时线程仍绑定着刚提交的旧事务资源，若加入当前事务就会把写操作挂在一个已经提交的
 *       边界上，结果不可预期。用独立事务把结果确定性落库。</li>
 * </ul>
 * 独立事务结果不随调用方后续回滚丢失；任务完成使用attempts作为抢占代次防止过期工作者覆盖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundNotificationTxService {

    /** 通知订单已退款。 */
    public static final String EVENT_ORDER_REFUNDED = "ORDER_REFUNDED";

    /** 回补库存。 */
    public static final String EVENT_STOCK_RESTOCK = "STOCK_RESTOCK";

    /** 事件类型集合；落库顺序固定，便于测试与排查（ORDER_REFUNDED、STOCK_RESTOCK）。 */
    private static final List<String> EVENTS =
            List.of(EVENT_ORDER_REFUNDED, EVENT_STOCK_RESTOCK);

    private static final String STATUS_PENDING = "PENDING";

    private final RefundNotificationTaskMapper taskMapper;

    /**
     * 与退款审核同一事务落库两条任务。已存在的（含已 DONE 的）不覆盖、不重置。
     *
     * <p>不加 REQUIRES_NEW，也不自带事务边界：它必须参与调用方的退款审核事务，
     * 审核回滚时两条任务一起消失。调用方是 {@code RefundServiceImpl#doAudit}（带 {@code @Transactional}）。
     *
     * @return 本次写入的任务条数（两个事件；已存在时由 upsert 静默保留原行）
     */
    @Transactional(rollbackFor = Exception.class)
    public int createTasks(String orderId) {
        int created = 0;
        for (String eventType : EVENTS) {
            // NULL explicitly means immediately due; avoid DB timestamp rounding a fresh task into the future.
            created += taskMapper.insert(orderId, eventType, STATUS_PENDING, null);
        }
        log.info("退款通知任务已落库, orderId={}, events={}", orderId, EVENTS);
        return created;
    }

    /**
     * 独立事务重新读取任务（afterCommit 回调里不读可能残留的旧事务快照）。
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public List<RefundNotificationTask> findByOrderId(String orderId) {
        return taskMapper.selectByOrderId(orderId);
    }

    /**
     * 条件 UPDATE 原子抢占，成功返回抢占后的任务快照。
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public RefundNotificationTask claim(String orderId, String eventType, LocalDateTime leaseUntil) {
        LocalDateTime now = LocalDateTime.now();
        List<RefundNotificationTask> tasks = taskMapper.selectByOrderId(orderId);
        for (RefundNotificationTask task : tasks) {
            if (!eventType.equals(task.getEventType())) {
                continue;
            }
            if (taskMapper.claim(task.getEventId(), now, leaseUntil) == 0) {
                return null;
            }
            return taskMapper.selectByOrderId(orderId).stream()
                    .filter(item -> eventType.equals(item.getEventType()))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public int markDone(RefundNotificationTask task) {
        return taskMapper.markDone(task.getEventId(), task.getAttempts(), LocalDateTime.now());
    }

    /**
     * @param sanitizedError 已脱敏的失败原因（不落凭据与异常堆栈）
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public int markFailed(RefundNotificationTask task, String sanitizedError, LocalDateTime nextRetryAt) {
        return taskMapper.markFailed(task.getEventId(), task.getAttempts(), sanitizedError, nextRetryAt,
                LocalDateTime.now());
    }

    /**
     * 调度扫描：到期可投递的任务（PENDING 已到重试时间，或 PROCESSING 租约过期）。
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public List<RefundNotificationTask> selectDue(LocalDateTime now, int limit) {
        return taskMapper.selectDue(now, limit);
    }

    /**
     * 退款审核提交后立即投递：只挑仍是 PENDING 的任务，避免重复投递已 DONE 的。
     */
    public List<RefundNotificationTask> findPending(List<RefundNotificationTask> tasks) {
        return tasks.stream().filter(task -> STATUS_PENDING.equals(task.getStatus())).toList();
    }
}
