package com.mall.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 管理员变更用户角色入参。
 */
@Data
public class RoleRequest {

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(BUYER|SELLER|ADMIN)$", message = "角色只能是 BUYER、SELLER 或 ADMIN")
    private String role;
}
