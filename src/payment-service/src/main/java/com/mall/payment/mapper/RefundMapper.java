package com.mall.payment.mapper;

import com.mall.payment.common.RefundStatus;
import com.mall.payment.dto.response.FinanceSummaryRows;
import com.mall.payment.entity.Refund;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款单数据访问。
 */
public interface RefundMapper {

    int insert(Refund refund);

    Refund selectById(@Param("refundId") String refundId);

    List<Refund> selectPage(@Param("status") RefundStatus status,
                            @Param("buyerId") String buyerId,
                            @Param("sellerId") String sellerId,
                            @Param("offset") int offset,
                            @Param("size") int size);

    long count(@Param("status") RefundStatus status,
               @Param("buyerId") String buyerId,
               @Param("sellerId") String sellerId);

    /** 历史退款没有 seller_id 时，由支付服务反查订单归属后补齐。 */
    List<Refund> selectMissingSellerId(@Param("limit") int limit);

    int updateSellerId(@Param("refundId") String refundId, @Param("sellerId") String sellerId);

    /**
     * 只允许把待审核的退款单改成最终状态，防止重复审核。
     */
    int updateAudit(@Param("refundId") String refundId,
                    @Param("status") RefundStatus status,
                    @Param("auditedBy") String auditedBy,
                    @Param("auditRemark") String auditRemark,
                    @Param("auditedAt") LocalDateTime auditedAt);

    /** 管理员资金汇总：成功退款与待审退款的金额口径（一条 SQL，不分页）。 */
    FinanceSummaryRows.Totals selectFinanceTotals();

    /** 管理员资金汇总：成功退款按 audited_at 归日金额（全量）。 */
    List<FinanceSummaryRows.DailyRefund> selectFinanceDaily();
}
