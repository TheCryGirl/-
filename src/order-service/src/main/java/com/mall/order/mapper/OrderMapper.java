package com.mall.order.mapper;

import com.mall.order.dto.response.FinanceSummaryRows;
import com.mall.order.entity.Order;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderMapper {

    int insert(Order order);

    Order selectById(@Param("orderId") String orderId);

    Order lockById(@Param("orderId") String orderId);

    /** 主订单下的全部店铺子订单（#135）。 */
    List<Order> selectByMainOrderId(@Param("mainOrderId") String mainOrderId);

    List<Order> selectList(@Param("buyerId") String buyerId,
                           @Param("sellerId") String sellerId,
                           @Param("status") String status,
                           @Param("offset") int offset,
                           @Param("size") int size);

    long countList(@Param("buyerId") String buyerId,
                   @Param("sellerId") String sellerId,
                   @Param("status") String status);

    List<String> selectTimeoutCandidates(@Param("deadline") LocalDateTime deadline,
                                         @Param("limit") int limit);

    List<String> selectUnreleasedCancelled(@Param("limit") int limit);

    int markPaid(@Param("orderId") String orderId, @Param("paidAt") LocalDateTime paidAt);

    int markShipped(@Param("orderId") String orderId,
                    @Param("expressCompany") String expressCompany,
                    @Param("trackingNo") String trackingNo,
                    @Param("shippedAt") LocalDateTime shippedAt);

    /**
     * 自动确认收货的候选：SHIPPED 且发货时间早于截止点（任务 #138）。
     * 没有 shipped_at 的历史单不会被扫到，由迁移脚本回填后才会进入。
     */
    List<String> selectAutoConfirmCandidates(@Param("deadline") LocalDateTime deadline,
                                             @Param("limit") int limit);

    /**
     * 自动确认收货的 CAS 抢占：只把仍是 SHIPPED 的单改成 COMPLETED。
     * 返回 0 表示已被买家手动确认、或被另一个调度实例抢先处理，调用方直接跳过。
     */
    int markAutoCompleted(@Param("orderId") String orderId, @Param("updatedAt") LocalDateTime updatedAt);

    int markCompleted(@Param("orderId") String orderId, @Param("updatedAt") LocalDateTime updatedAt);

    int markCancelled(@Param("orderId") String orderId, @Param("updatedAt") LocalDateTime updatedAt);

    int markRefunded(@Param("orderId") String orderId, @Param("updatedAt") LocalDateTime updatedAt);

    int markInventoryReleased(@Param("orderId") String orderId);
    /** 把 inventory_released 复位为 0，供释放失败后交给补偿任务重试 */
    int resetInventoryReleased(@Param("orderId") String orderId);

    int softDelete(@Param("orderId") String orderId);

    /** 管理员资金汇总：全部未删订单的总量口径（一条 SQL 聚合，不分页）。 */
    FinanceSummaryRows.Totals selectFinanceTotals();

    /** 管理员资金汇总：按状态分组计数，全部未删订单。 */
    List<FinanceSummaryRows.StatusCount> selectFinanceByStatus();

    /** 管理员资金汇总：按 paid_at 归日统计支付订单数与金额（pay 状态）。 */
    List<FinanceSummaryRows.DailyPaid> selectFinanceDaily();
}
