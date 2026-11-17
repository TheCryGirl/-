package com.mall.payment.dto.request;

import com.mall.payment.common.ArbitrationDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员平台仲裁裁定（#143）。仅 ADMIN 可调用。
 * decision 必填；remark 裁定理由必填，写入时间线。
 */
@Data
public class ArbitrateRequest {

    @NotNull(message = "裁定结果不能为空")
    private ArbitrationDecision decision;

    @NotBlank(message = "裁定理由不能为空")
    @Size(max = 255, message = "裁定理由过长")
    private String remark;
}