package com.mall.payment.mapper;

import com.mall.payment.dto.internal.OrderCompletion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 结算候选订单只读视图（#144 第二块）。
 *
 * <p>按组长口径直接读 order-service 的 order 表（跨库 mall_order.order，两服务共用
 * 同一个 MySQL 实例）；H2 测试里在 schema-h2.sql 建同名 schema 与表，SQL 两端一致。
 */
@Mapper
public interface OrderCompletionMapper {

    /**
     * 周期内已完成且尚未结算、无成功退款的子订单。
     * 完成时间以 order 表的 updated_at 为准：order-service 的 markCompleted
     * 只改 status 与 updated_at，且 COMPLETED 之后除退款外没有业务更新。
     */
    List<OrderCompletion> selectCompletedInPeriod(@Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end);
}
