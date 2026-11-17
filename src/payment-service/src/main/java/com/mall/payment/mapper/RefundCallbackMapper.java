package com.mall.payment.mapper;

import com.mall.payment.entity.RefundCallback;
import org.apache.ibatis.annotations.Param;

/**
 * 退款回调流水数据访问（#137）。去重靠 uk_channel_notify 唯一约束。
 */
public interface RefundCallbackMapper {

    int insert(RefundCallback callback);

    int updateProcessStatus(@Param("callbackId") Long callbackId,
                            @Param("processStatus") String processStatus,
                            @Param("errorMessage") String errorMessage);
}