package com.mall.user.entity;

import com.mall.user.common.Role;
import com.mall.user.common.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，与 user 表一一对应。
 */
@Data
public class User {

    private String userId;
    private String account;
    private String passwordHash;
    private String nickname;
    private String phone;
    private String avatar;
    private Role role;
    private UserStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Boolean isDeleted;
}
