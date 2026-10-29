package com.mall.payment.dto.request;

import com.mall.payment.common.AfterSaleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 买家申请售后。
 *
 * <p>仅退款/退货退款必须传大于 0 的金额；换货金额忽略。
 */
@Data
public class CreateAfterSaleRequest {

    @NotBlank(message = "订单ID不能为空")
    @Size(max = 32, message = "订单ID过长")
    private String orderId;

    @NotNull(message = "售后类型不能为空")
    private AfterSaleType type;

    private Long amount;

    @Size(max = 255, message = "售后原因过长")
    private String reason;
}
