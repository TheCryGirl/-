package com.mall.payment.mapper;

import com.mall.payment.common.RefundStatus;
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
                            @Param("offset") int offset,
                            @Param("size") int size);

    long count(@Param("status") RefundStatus status, @Param("buyerId") String buyerId);

    /**
     * 只允许把待审核的退款单改成最终状态，防止重复审核。
     */
    int updateAudit(@Param("refundId") String refundId,
                    @Param("status") RefundStatus status,
                    @Param("auditedBy") String auditedBy,
                    @Param("auditRemark") String auditRemark,
                    @Param("auditedAt") LocalDateTime auditedAt);
}
