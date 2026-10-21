package com.mall.user.controller;

import com.mall.user.common.BizException;
import com.mall.user.common.ErrorCode;
import com.mall.user.common.Result;
import com.mall.user.dto.request.*;
import com.mall.user.dto.response.AddressResponse;
import com.mall.user.dto.response.LoginResponse;
import com.mall.user.dto.response.PageResponse;
import com.mall.user.dto.response.UserResponse;
import com.mall.user.service.AddressService;
import com.mall.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户服务接口。服务内部路径不带 /api，与《05-接口契约》1.2 节一致。
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final UserService userService;
    private final AddressService addressService;

    @PostMapping("/register")
    public Result<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        String userId = userService.register(request);
        return Result.success(Map.of("userId", userId));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("X-User-Id") String userId) {
        // 网关已校验令牌，这里只需返回成功；若后续接入令牌黑名单可在此处理
        return Result.success(null);
    }

    @GetMapping("/me")
    public Result<UserResponse> me(@RequestHeader("X-User-Id") String userId) {
        return Result.success(userService.getCurrentUser(userId));
    }

    @PutMapping("/me")
    public Result<UserResponse> updateMe(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return Result.success(userService.updateProfile(userId, request));
    }

    @GetMapping
    public Result<PageResponse<UserResponse>> listUsers(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String roleFilter,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        if (!ROLE_ADMIN.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限执行该操作");
        }
        return Result.success(userService.listUsers(page, size, roleFilter, status, keyword));
    }

    @PutMapping("/{userId}/status")
    public Result<Void> updateStatus(
            @RequestHeader("X-User-Id") String operatorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("userId") String targetUserId,
            @Valid @RequestBody UpdateStatusRequest request) {
        if (!ROLE_ADMIN.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限执行该操作");
        }
        userService.updateStatus(operatorId, targetUserId, request);
        return Result.success(null);
    }

    @PutMapping("/{userId}/role")
    public Result<Void> updateRole(
            @RequestHeader("X-User-Id") String operatorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("userId") String targetUserId,
            @Valid @RequestBody RoleRequest request) {
        if (!ROLE_ADMIN.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限执行该操作");
        }
        userService.updateRole(operatorId, targetUserId, request);
        return Result.success(null);
    }

    @GetMapping("/{userId}/addresses")
    public Result<List<AddressResponse>> listAddresses(
            @RequestHeader("X-User-Id") String currentUserId,
            @PathVariable("userId") String userId) {
        checkOwner(currentUserId, userId);
        return Result.success(addressService.listAddresses(userId));
    }

    @PostMapping("/addresses")
    public Result<AddressResponse> createAddress(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody AddressRequest request) {
        return Result.success(addressService.createAddress(userId, request));
    }

    @PutMapping("/addresses/{addressId}")
    public Result<AddressResponse> updateAddress(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("addressId") String addressId,
            @Valid @RequestBody AddressRequest request) {
        return Result.success(addressService.updateAddress(userId, addressId, request));
    }

    @DeleteMapping("/addresses/{addressId}")
    public Result<Void> deleteAddress(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("addressId") String addressId) {
        addressService.deleteAddress(userId, addressId);
        return Result.success(null);
    }

    @PutMapping("/addresses/{addressId}/default")
    public Result<Void> setDefaultAddress(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("addressId") String addressId) {
        addressService.setDefaultAddress(userId, addressId);
        return Result.success(null);
    }

    private void checkOwner(String currentUserId, String userId) {
        if (!currentUserId.equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限查看他人地址");
        }
    }
}
