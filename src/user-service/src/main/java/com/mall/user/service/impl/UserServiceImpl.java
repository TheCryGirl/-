package com.mall.user.service.impl;

import com.mall.user.common.*;
import com.mall.user.dto.request.LoginRequest;
import com.mall.user.dto.request.RegisterRequest;
import com.mall.user.dto.request.RoleRequest;
import com.mall.user.dto.request.UpdateProfileRequest;
import com.mall.user.dto.request.UpdateStatusRequest;
import com.mall.user.dto.response.LoginResponse;
import com.mall.user.dto.response.PageResponse;
import com.mall.user.dto.response.UserResponse;
import com.mall.user.entity.User;
import com.mall.user.mapper.UserMapper;
import com.mall.user.service.UserService;
import com.mall.user.util.JwtUtil;
import com.mall.user.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String ROLE_ADMIN = "ADMIN";

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional
    public String register(RegisterRequest request) {
        // 1. 账号唯一性校验
        if (userMapper.selectByAccount(request.getAccount()) != null) {
            throw new BizException(ErrorCode.ACCOUNT_EXISTS, "账号已存在");
        }
        // 2. 昵称唯一性校验
        if (userMapper.selectByNickname(request.getNickname()) != null) {
            throw new BizException(ErrorCode.NICKNAME_EXISTS, "昵称已被占用");
        }

        User user = new User();
        user.setUserId(IdGenerator.userId());
        user.setAccount(request.getAccount());
        user.setPasswordHash(PasswordUtil.hash(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setPhone(request.getPhone());
        user.setRole(Role.BUYER);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setIsDeleted(Boolean.FALSE);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发场景下兜底：按错误信息判断是账号还是昵称冲突
            String message = e.getMessage();
            if (message != null && message.contains("uk_account")) {
                throw new BizException(ErrorCode.ACCOUNT_EXISTS, "账号已存在");
            }
            if (message != null && message.contains("uk_nickname")) {
                throw new BizException(ErrorCode.NICKNAME_EXISTS, "昵称已被占用");
            }
            throw new BizException(ErrorCode.PARAM_INVALID, "账号或昵称已被占用");
        }

        log.info("用户注册成功, userId={}, account={}", user.getUserId(), maskAccount(user.getAccount()));
        return user.getUserId();
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectByAccount(request.getAccount());
        if (user == null || !PasswordUtil.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.ACCOUNT_OR_PASSWORD_ERROR, "账号或密码错误");
        }
        if (user.getStatus() == UserStatus.FROZEN) {
            throw new BizException(ErrorCode.ACCOUNT_FROZEN, "账号已被冻结");
        }

        String token = jwtUtil.generateToken(user.getUserId(), user.getRole());
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUser(toResponse(user));

        log.info("用户登录成功, userId={}, role={}", user.getUserId(), user.getRole());
        return response;
    }

    @Override
    public UserResponse getCurrentUser(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        return toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }

        // 如果修改昵称，需要唯一性校验
        if (request.getNickname() != null && !request.getNickname().equals(user.getNickname())) {
            if (userMapper.selectByNickname(request.getNickname()) != null) {
                throw new BizException(ErrorCode.NICKNAME_EXISTS, "昵称已被占用");
            }
            user.setNickname(request.getNickname());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        user.setUpdatedAt(LocalDateTime.now());
        user.setUpdatedBy(userId);

        try {
            userMapper.updateById(user);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.NICKNAME_EXISTS, "昵称已被占用");
        }

        return toResponse(user);
    }

    @Override
    public PageResponse<UserResponse> listUsers(int page, int size, String role, String status, String keyword) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = 20;
        }
        if (size > 100) {
            size = 100;
        }
        int offset = (page - 1) * size;
        long total = userMapper.countPage(role, status, keyword);
        List<User> users = userMapper.selectPage(role, status, keyword, offset, size);
        List<UserResponse> list = users.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return PageResponse.of(page, size, total, list);
    }

    @Override
    public void updateStatus(String operatorId, String targetUserId, UpdateStatusRequest request) {
        User operator = userMapper.selectById(operatorId);
        if (operator == null || !ROLE_ADMIN.equals(operator.getRole().name())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限执行该操作");
        }
        User target = userMapper.selectById(targetUserId);
        if (target == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        // 避免管理员误操作把自己的账号冻结，导致无人可管理系统
        if (targetUserId.equals(operatorId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能变更自己的账号状态");
        }

        UserStatus newStatus = UserStatus.valueOf(request.getStatus());
        userMapper.updateStatusById(targetUserId, newStatus.name());
        log.info("管理员变更用户状态, operatorId={}, targetUserId={}, status={}", operatorId, targetUserId, newStatus);
    }

    @Override
    @Transactional
    public void updateRole(String operatorId, String targetUserId, RoleRequest request) {
        User operator = userMapper.selectById(operatorId);
        if (operator == null || operator.getRole() != Role.ADMIN) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限执行该操作");
        }
        User target = userMapper.selectById(targetUserId);
        if (target == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        // 避免管理员把自己降级后无人可管理系统
        if (targetUserId.equals(operatorId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能变更自己的角色");
        }

        Role newRole = Role.valueOf(request.getRole());
        if (target.getRole() == newRole) {
            return;
        }
        userMapper.updateRoleById(targetUserId, newRole.name(), operatorId);
        log.info("管理员变更用户角色, operatorId={}, targetUserId={}, role={}", operatorId, targetUserId, newRole);
    }

    private UserResponse toResponse(User user) {
        UserResponse response = new UserResponse();
        response.setUserId(user.getUserId());
        response.setAccount(user.getAccount());
        response.setNickname(user.getNickname());
        response.setPhone(user.getPhone());
        response.setAvatar(user.getAvatar());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setCreatedAt(user.getCreatedAt());
        return response;
    }

    private String maskAccount(String account) {
        if (account == null || account.length() <= 4) {
            return "****";
        }
        return account.substring(0, 2) + "****" + account.substring(account.length() - 2);
    }
}
