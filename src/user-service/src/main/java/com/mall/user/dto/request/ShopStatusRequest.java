package com.mall.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理员变更店铺营业状态入参。
 */
@Data
public class ShopStatusRequest {

    @NotBlank(message = "营业状态不能为空")
    private String status;
}
