package com.mall.payment.mapper;

import com.mall.payment.entity.RefundNotificationTask;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款通知任务数据访问。
 */
public interface RefundNotificationTaskMapper {

    int insert(@Param("orderId") String orderId,
               @Param("eventType") String eventType,
               @Param("status") String status,
               @Param("nextRetryAt") LocalDateTime nextRetryAt);

    List<RefundNotificationTask> selectByOrderId(@Param("orderId") String orderId);

    /**
     * 待处理的任务：PENDING 且到了重试时间，或 PROCESSING 但租约已过期（实例崩溃后回收）。
     */
    List<RefundNotificationTask> selectDue(@Param("now") LocalDateTime now,
                                           @Param("limit") int limit);

    /**
     * 条件 UPDATE 原子抢占：只有 PENDING 已到期、或 PROCESSING 租约过期的行能被改写。
     *
     * @return 1 表示抢到，0 表示已被其他实例处理
     */
    int claim(@Param("eventId") Long eventId,
              @Param("now") LocalDateTime now,
              @Param("leaseUntil") LocalDateTime leaseUntil);

    int markDone(@Param("eventId") Long eventId,
                 @Param("attempts") Integer attempts,
                 @Param("updatedAt") LocalDateTime updatedAt);

    int markFailed(@Param("eventId") Long eventId,
                   @Param("attempts") Integer attempts,
                   @Param("lastError") String lastError,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt,
                   @Param("updatedAt") LocalDateTime updatedAt);
}
