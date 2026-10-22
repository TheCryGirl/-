package com.mall.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 管理员变更用户状态入参。
 */
@Data
public class UpdateStatusRequest {

    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "^(ACTIVE|FROZEN)$", message = "状态只能是 ACTIVE 或 FROZEN")
    private String status;
}
