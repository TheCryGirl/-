package com.mall.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 换货流程：卖家确认收到退货并重新发货。
 */
@Data
public class ReshipRequest {

    @NotBlank(message = "换货物流单号不能为空")
    @Size(max = 64, message = "物流单号过长")
    private String trackingNo;

    @Size(max = 255, message = "处理备注过长")
    private String remark;
}
