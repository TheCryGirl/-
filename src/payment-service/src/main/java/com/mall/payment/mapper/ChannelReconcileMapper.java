package com.mall.payment.mapper;

import com.mall.payment.entity.Payment;
import com.mall.payment.entity.ReconcileBatch;
import com.mall.payment.entity.ReconcileDifference;
import com.mall.payment.entity.Refund;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 渠道对账数据访问（#137）。本地侧只读，差异只写 reconcile_* 表。
 */
public interface ChannelReconcileMapper {

    int insertBatch(ReconcileBatch batch);

    int insertDifference(ReconcileDifference difference);

    List<ReconcileBatch> selectBatches(@Param("channel") String channel,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    long countBatches(@Param("channel") String channel);

    List<ReconcileDifference> selectDifferences(@Param("batchId") String batchId,
                                                @Param("diffType") String diffType,
                                                @Param("status") String status,
                                                @Param("offset") int offset,
                                                @Param("size") int size);

    long countDifferences(@Param("batchId") String batchId,
                          @Param("diffType") String diffType,
                          @Param("status") String status);

    ReconcileDifference selectDifferenceById(@Param("differenceId") Long differenceId);

    int resolveDifference(@Param("differenceId") Long differenceId,
                          @Param("resolvedBy") String resolvedBy,
                          @Param("remark") String remark,
                          @Param("resolvedAt") LocalDateTime resolvedAt);

    /** 对账只读本地支付单：指定渠道的成功/已退款支付 */
    List<Payment> selectPaidPaymentsByChannel(@Param("channel") String channel);

    /** 对账只读本地退款单：指定渠道支付单下的成功退款 */
    List<Refund> selectSuccessRefundsByChannel(@Param("channel") String channel);
}