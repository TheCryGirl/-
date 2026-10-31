package com.mall.order.mapper;

import com.mall.order.entity.OrderIdempotency;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface IdempotencyMapper {

    int insert(OrderIdempotency record);

    OrderIdempotency selectByKey(@Param("idemKey") String idemKey);

    int markSuccess(@Param("idemKey") String idemKey,
                    @Param("orderId") String orderId,
                    @Param("updatedAt") LocalDateTime updatedAt);

    int deleteByKey(@Param("idemKey") String idemKey);

    int deleteStale(@Param("cutoff") LocalDateTime cutoff);

    /**
     * 抢占库存预占的执行权。判断与更新在同一条 SQL 中完成，
     * 返回 1 表示本次调用抢到，返回 0 表示已被其他请求持有。
     * 允许从 PROCESSING（首个请求尚未开始）与 FAILED（上次失败已退回）抢入。
     */
    int claimReserving(@Param("idemKey") String idemKey,
                       @Param("updatedAt") LocalDateTime updatedAt);

    /** 预占与落库成功后置为终态，返回 0 说明执行权已被回收。 */
    int markSuccessIfReserving(@Param("idemKey") String idemKey,
                               @Param("orderId") String orderId,
                               @Param("updatedAt") LocalDateTime updatedAt);

    /** 执行权退回，允许同一幂等键重试。 */
    int markFailed(@Param("idemKey") String idemKey,
                   @Param("updatedAt") LocalDateTime updatedAt);

    /** 回收长时间停留在 RESERVING 的记录（持锁进程崩溃或调用超时）。 */
    String selectStaleReservingKey(@Param("cutoff") LocalDateTime cutoff);

    int resetStaleReserving(@Param("idemKey") String idemKey,
                            @Param("cutoff") LocalDateTime cutoff,
                            @Param("updatedAt") LocalDateTime updatedAt);
}
