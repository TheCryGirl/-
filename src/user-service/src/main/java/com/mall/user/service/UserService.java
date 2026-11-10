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

    /**
     * 用户自助开通卖家身份。
     *
     * <p>业务规则调整后，普通用户既能买也能卖：首次进入卖家工作台时调用本方法，
     * 把 BUYER 升级为 SELLER 并重新签发令牌，避免用户退出重登。
     */
    LoginResponse activateSeller(String userId);

    UserResponse getCurrentUser(String userId);

    UserResponse updateProfile(String userId, UpdateProfileRequest request);

    PageResponse<UserResponse> listUsers(int page, int size, String role, String status, String keyword);

    void updateStatus(String operatorId, String targetUserId, UpdateStatusRequest request);

    void updateRole(String operatorId, String targetUserId, RoleRequest request);
}
