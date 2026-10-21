package com.mall.user.service;

import com.mall.user.dto.request.LoginRequest;
import com.mall.user.dto.request.RegisterRequest;
import com.mall.user.dto.request.RoleRequest;
import com.mall.user.dto.request.UpdateProfileRequest;
import com.mall.user.dto.request.UpdateStatusRequest;
import com.mall.user.dto.response.LoginResponse;
import com.mall.user.dto.response.PageResponse;
import com.mall.user.dto.response.UserResponse;

/**
 * 用户服务接口。
 */
public interface UserService {

    String register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    UserResponse getCurrentUser(String userId);

    UserResponse updateProfile(String userId, UpdateProfileRequest request);

    PageResponse<UserResponse> listUsers(int page, int size, String role, String status, String keyword);

    void updateStatus(String operatorId, String targetUserId, UpdateStatusRequest request);

    void updateRole(String operatorId, String targetUserId, RoleRequest request);
}
