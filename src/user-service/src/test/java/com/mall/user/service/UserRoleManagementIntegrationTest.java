package com.mall.user.service;

import com.mall.user.common.BizException;
import com.mall.user.common.ErrorCode;
import com.mall.user.common.Role;
import com.mall.user.dto.request.RegisterRequest;
import com.mall.user.dto.request.RoleRequest;
import com.mall.user.entity.User;
import com.mall.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 管理员变更用户角色的集成测试。
 */
@SpringBootTest
@ActiveProfiles("test")
class UserRoleManagementIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Test
    void adminCanPromoteBuyerToSeller() {
        String adminId = registerUser("adm", "管理员");
        String buyerId = registerUser("buy", "买家");
        userMapper.updateRoleById(adminId, Role.ADMIN.name(), "TEST");

        userService.updateRole(adminId, buyerId, roleRequest("SELLER"));

        User updated = userMapper.selectById(buyerId);
        assertEquals(Role.SELLER, updated.getRole());
        assertEquals(adminId, updated.getUpdatedBy());
    }

    @Test
    void nonAdminCannotChangeRole() {
        String buyerId = registerUser("nrm", "普通用户");
        String targetId = registerUser("tgt", "目标用户");

        BizException exception = assertThrows(BizException.class,
                () -> userService.updateRole(buyerId, targetId, roleRequest("SELLER")));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
        assertEquals(Role.BUYER, userMapper.selectById(targetId).getRole());
    }

    @Test
    void adminCannotChangeOwnRole() {
        String adminId = registerUser("slf", "本人管理员");
        userMapper.updateRoleById(adminId, Role.ADMIN.name(), "TEST");

        BizException exception = assertThrows(BizException.class,
                () -> userService.updateRole(adminId, adminId, roleRequest("BUYER")));

        assertEquals(ErrorCode.PARAM_INVALID, exception.getCode());
        assertEquals(Role.ADMIN, userMapper.selectById(adminId).getRole());
    }

    private String registerUser(String accountPrefix, String nicknamePrefix) {
        // 账号与昵称字段最多 20 位，取纳秒尾数缩短后缀
        String suffix = String.valueOf(System.nanoTime() % 1_000_000_000L);
        RegisterRequest request = new RegisterRequest();
        request.setAccount(accountPrefix + suffix);
        request.setPassword("123456");
        request.setNickname(nicknamePrefix + suffix);
        return userService.register(request);
    }

    private RoleRequest roleRequest(String role) {
        RoleRequest request = new RoleRequest();
        request.setRole(role);
        return request;
    }
}
