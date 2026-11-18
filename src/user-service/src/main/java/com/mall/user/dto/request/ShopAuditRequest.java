package com.mall.user.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员审核入驻申请入参（#144）。
 */
@Data
public class ShopAuditRequest {

    /** APPROVED 通过 / REJECTED 驳回 */
    @NotBlank(message = "审核结论不能为空")
    private String decision;

    /** 驳回时必填，卖家侧可见 */
    @Size(max = 255, message = "审核备注不能超过 255 字")
    private String reason;

    /**
     * 保证金金额（分）。审核通过时登记：传 0 表示已要求但未缴纳，
     * 不传表示沿用已登记金额；保证金仅作记录，不拦截发布。
     */
    @Min(value = 0, message = "保证金不能为负数")
    private Long depositAmount;
}
