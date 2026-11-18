package com.mall.payment.mapper;

import com.mall.payment.entity.SettlementItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 结算明细数据访问层（#144 第二块）。order_id 唯一键保证一笔子订单只结算一次。
 */
@Mapper
public interface SettlementItemMapper {

    /** 唯一键冲突时静默跳过（幂等插入） */
    int insertIgnoreDuplicate(SettlementItem item);

    List<SettlementItem> lockBySettlementId(@Param("settlementId") String settlementId);

    List<SettlementItem> selectBySettlementIds(@Param("settlementIds") List<String> settlementIds);
}
