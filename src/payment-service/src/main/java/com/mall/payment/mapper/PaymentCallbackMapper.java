package com.mall.payment.mapper;

import com.mall.payment.common.CallbackProcessStatus;
import com.mall.payment.entity.PaymentCallback;
import org.apache.ibatis.annotations.Param;

/**
 * 支付回调流水数据访问。
 */
public interface PaymentCallbackMapper {

    int insert(PaymentCallback callback);

    int updateProcessStatus(@Param("callbackId") Long callbackId,
                            @Param("processStatus") CallbackProcessStatus processStatus,
                            @Param("errorMessage") String errorMessage);

    PaymentCallback selectByTradeNo(@Param("tradeNo") String tradeNo);
}
