package com.mall.user.service;

import com.mall.user.common.BizException;
import com.mall.user.common.ErrorCode;
import com.mall.user.dto.request.LoginRequest;
import com.mall.user.dto.request.RegisterRequest;
import com.mall.user.dto.response.LoginResponse;
import com.mall.user.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户注册登录集成测试。
 */
@SpringBootTest
@ActiveProfiles("test")
class UserAuthIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void registerThenLoginSuccess() {
        String account = "test" + System.currentTimeMillis();
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setAccount(account);
        registerRequest.setPassword("123456");
        registerRequest.setNickname("测试" + System.currentTimeMillis());
        registerRequest.setPhone("13800138000");

        String userId = userService.register(registerRequest);
        assertNotNull(userId);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setAccount(account);
        loginRequest.setPassword("123456");
        LoginResponse response = userService.login(loginRequest);

        assertNotNull(response.getToken());
        assertEquals(userId, response.getUser().getUserId());
        assertNotNull(jwtUtil.parseToken(response.getToken()));
    }

    @Test
    void loginWithWrongPasswordFails() {
        String account = "wrong" + System.currentTimeMillis();
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setAccount(account);
        registerRequest.setPassword("123456");
        registerRequest.setNickname("错误密码" + System.currentTimeMillis());
        userService.register(registerRequest);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setAccount(account);
        loginRequest.setPassword("wrong-password");

        BizException ex = assertThrows(BizException.class, () -> userService.login(loginRequest));
        assertEquals(ErrorCode.ACCOUNT_OR_PASSWORD_ERROR, ex.getCode());
    }

    @Test
    void duplicateAccountFails() {
        String account = "dup" + System.currentTimeMillis();
        RegisterRequest request = new RegisterRequest();
        request.setAccount(account);
        request.setPassword("123456");
        request.setNickname("重复账号A" + System.currentTimeMillis());
        userService.register(request);

        RegisterRequest request2 = new RegisterRequest();
        request2.setAccount(account);
        request2.setPassword("123456");
        request2.setNickname("重复账号B" + System.currentTimeMillis());

        BizException ex = assertThrows(BizException.class, () -> userService.register(request2));
        assertEquals(ErrorCode.ACCOUNT_EXISTS, ex.getCode());
    }
}
