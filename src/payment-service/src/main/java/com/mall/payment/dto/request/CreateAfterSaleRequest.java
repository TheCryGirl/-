package com.mall.payment.dto.request;

import com.mall.payment.common.AfterSaleReasonCode;
import com.mall.payment.common.AfterSaleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 买家申请售后。
 *
 * <p>仅退款/退货退款必须传大于 0 的金额；换货金额忽略。
 *
 * <p>#143 增量：reasonCode 为固定原因枚举，选 OTHER 时 reason 作为补充说明必填；
 * attachmentUrls 为预先上传的凭证站内路径，单次最多 3 张。两者均为新增可选字段，
 * 不改变既有调用语义。
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

    private AfterSaleReasonCode reasonCode;

    @Size(max = 3, message = "单次最多上传 3 张凭证")
    private List<@Size(max = 255, message = "凭证路径过长") String> attachmentUrls;
}
