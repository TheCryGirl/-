package com.mall.payment.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SettlementLedgerMapper {
    record PaidItem(String settlementId, String orderId, long grossAmount, long commissionAmount,
                    String commissionSnapshot, long refundedAmount, LocalDateTime periodEnd,
                    long totalRefunded, long recordedRefunded, long recordedDeduction) {}

    record Adjustment(long id, String orderId, String sourceSettlementId, long amount, long appliedAmount) {}

    record Payout(String reference, String operatorId, long amount, LocalDateTime paidAt, String remark) {}

    int ensureSeller(@Param("sellerId") String sellerId);
    String lockSeller(@Param("sellerId") String sellerId);
    List<PaidItem> selectPaidRefundChanges(@Param("sellerId") String sellerId);
    List<String> selectSellersWithAdjustments(@Param("periodStart") LocalDateTime periodStart);
    int insertAdjustment(@Param("sellerId") String sellerId, @Param("item") PaidItem item,
                         @Param("refunded") long refunded, @Param("amount") long amount);
    List<Adjustment> lockAvailableAdjustments(@Param("sellerId") String sellerId,
                                             @Param("periodStart") LocalDateTime periodStart);
    int clearApplied(@Param("settlementId") String settlementId);
    List<Applied> selectApplied(@Param("settlementId") String settlementId);
    record Applied(String sourceSettlementId, String orderId, long amount) {}
    int applyAdjustment(@Param("settlementId") String settlementId, @Param("adjustmentId") long adjustmentId,
                        @Param("amount") long amount);
    int insertPayout(@Param("settlementId") String settlementId, @Param("payout") Payout payout);
    Payout selectPayout(@Param("settlementId") String settlementId);
}
