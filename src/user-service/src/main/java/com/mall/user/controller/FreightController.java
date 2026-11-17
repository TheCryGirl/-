package com.mall.user.controller;

import com.mall.user.common.BizException;
import com.mall.user.common.ErrorCode;
import com.mall.user.common.Result;
import com.mall.user.dto.request.FreightConfigRequest;
import com.mall.user.dto.response.FreightConfigResponse;
import com.mall.user.service.FreightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 卖家运费设置（#136）。服务内部路径不带 /api，网关按 /api/shops/** 转发。
 */
@RestController
@RequestMapping("/shops")
@RequiredArgsConstructor
public class FreightController {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_SELLER = "SELLER";

    private final FreightService freightService;

    /** 卖家读取自己店铺的运费配置 */
    @GetMapping("/mine/freight")
    public Result<FreightConfigResponse> mine(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireSeller(role);
        return Result.success(freightService.getMine(userId));
    }

    /** 卖家保存运费配置（整体覆盖：模板 + 分地区规则） */
    @PutMapping("/mine/freight")
    public Result<FreightConfigResponse> saveMine(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody FreightConfigRequest request) {
        requireSeller(role);
        return Result.success(freightService.saveMine(userId, request));
    }

    private static void requireSeller(String role) {
        if (!ROLE_SELLER.equals(role) && !ROLE_ADMIN.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅卖家可配置运费");
        }
    }
}
