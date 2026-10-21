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

    /**
     * 只允许把待审核的退款单改成最终状态，防止重复审核。
     */
    int updateAudit(@Param("refundId") String refundId,
                    @Param("status") RefundStatus status,
                    @Param("auditedBy") String auditedBy,
                    @Param("auditRemark") String auditRemark,
                    @Param("auditedAt") LocalDateTime auditedAt);

    List<Refund> selectPage(@Param("status") String status,
                            @Param("offset") int offset,
                            @Param("size") int size);

    long countPage(@Param("status") String status);
}
