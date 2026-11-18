package com.mall.payment.mapper;

import com.mall.payment.entity.Settlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 结算单数据访问层（#144 第二块）。防重复靠 (seller_id, period_start) 唯一键，
 * 追加金额与状态流转都走条件 UPDATE，避免先查再改的竞态。
 */
@Mapper
public interface SettlementMapper {

    /** 唯一键冲突时静默跳过（幂等插入） */
    int insertIgnoreDuplicate(Settlement settlement);

    Settlement selectById(@Param("settlementId") String settlementId);

    List<String> selectPendingSellers(@Param("periodStart") LocalDateTime periodStart);

    List<Settlement> lockEditableBySeller(@Param("sellerId") String sellerId);

    Settlement lockBySellerPeriod(@Param("sellerId") String sellerId,
                                  @Param("periodStart") LocalDateTime periodStart);

    /** 只有 PENDING 状态允许更新表头金额。 */
    int setAmounts(@Param("settlementId") String settlementId,
                   @Param("gross") long gross,
                   @Param("commission") long commission,
                   @Param("net") long net,
                   @Param("operatorId") String operatorId);

    /** PENDING/CONFIRMED -> PAID 的条件流转；返回受影响行数 */
    int markPaid(@Param("settlementId") String settlementId,
                 @Param("operatorId") String operatorId);

    List<Settlement> selectPage(@Param("sellerId") String sellerId,
                                @Param("periodStart") LocalDateTime periodStart,
                                @Param("offset") int offset,
                                @Param("size") int size);

    long count(@Param("sellerId") String sellerId,
               @Param("periodStart") LocalDateTime periodStart);
}
