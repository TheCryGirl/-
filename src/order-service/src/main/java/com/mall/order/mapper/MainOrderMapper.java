package com.mall.order.mapper;

import com.mall.order.entity.MainOrder;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 主订单数据访问（#135）。状态迁移全部用条件 UPDATE，影响行数 0 表示已被并发处理。
 */
public interface MainOrderMapper {

    int insert(MainOrder mainOrder);

    MainOrder selectById(@Param("mainOrderId") String mainOrderId);

    List<MainOrder> selectByBuyer(@Param("buyerId") String buyerId,
                                  @Param("status") String status,
                                  @Param("offset") int offset,
                                  @Param("size") int size);

    long countByBuyer(@Param("buyerId") String buyerId, @Param("status") String status);

    /** 全部子订单已 PAID 时把主订单置 PAID；条件更新保证并发下只生效一次。 */
    int markPaidIfChildrenPaid(@Param("mainOrderId") String mainOrderId,
                               @Param("paidAt") LocalDateTime paidAt);

    /** PENDING_PAYMENT → CANCELLED 的条件抢占。 */
    int markCancelled(@Param("mainOrderId") String mainOrderId,
                      @Param("updatedAt") LocalDateTime updatedAt);

    /** 超时扫描候选：待支付且已过期的主订单。 */
    List<String> selectTimeoutCandidates(@Param("deadline") LocalDateTime deadline,
                                         @Param("limit") int limit);

    int softDelete(@Param("mainOrderId") String mainOrderId);
}