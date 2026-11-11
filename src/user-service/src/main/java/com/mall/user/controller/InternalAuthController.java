package com.mall.user.controller;

import com.mall.user.common.BizException;
import com.mall.user.common.ErrorCode;
import com.mall.user.common.Result;
import com.mall.user.dto.response.UserAuthState;
import com.mall.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Protected by ServiceIngressFilter; never routed through the public user gateway. */
@RestController
@RequestMapping("/users/internal/auth-state")
@RequiredArgsConstructor
public class InternalAuthController {
    private final UserMapper userMapper;

    @GetMapping("/{userId}")
    public Result<UserAuthState> state(@PathVariable String userId) {
        UserAuthState state = userMapper.selectAuthState(userId);
        if (state == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        return Result.success(state);
    }
}
