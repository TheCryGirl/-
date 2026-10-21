package com.mall.user.dto.response;

import com.mall.user.common.Role;
import com.mall.user.common.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息出参。
 */
@Data
public class UserResponse {

    private String userId;
    private String account;
    private String nickname;
    private String phone;
    private String avatar;
    private Role role;
    private UserStatus status;
    private LocalDateTime createdAt;
}
