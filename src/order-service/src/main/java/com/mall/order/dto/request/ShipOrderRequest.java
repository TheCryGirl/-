package com.mall.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发货请求（任务 #138）：快递公司与单号都必填。
 *
 * <p>快递公司是枚举编码（见 {@code ExpressCompany}），不接受自由文本；
 * 非法编码由 service 层统一转成 10001，与「缺字段」给同一个错误码。
 */
@Data
public class ShipOrderRequest {

    @NotBlank(message = "快递公司不能为空")
    @Size(max = 16, message = "快递公司编码不合法")
    private String expressCompany;

    @NotBlank(message = "物流单号不能为空")
    @Size(max = 64, message = "物流单号长度不能超过 64")
    private String trackingNo;
}
