package com.mall.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 重复推送指定交易号，用于验证回调幂等。
 */
@Data
public class MockDuplicateRequest {

    @NotBlank(message = "交易号不能为空")
    private String tradeNo;
}
