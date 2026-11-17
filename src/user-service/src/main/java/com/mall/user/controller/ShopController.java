package com.mall.user.controller;

import com.mall.user.common.BizException;
import com.mall.user.common.ErrorCode;
import com.mall.user.common.Result;
import com.mall.user.dto.request.ShopRequest;
import com.mall.user.dto.request.ShopStatusRequest;
import com.mall.user.dto.response.FollowedShopResponse;
import com.mall.user.dto.response.PageResponse;
import com.mall.user.dto.response.ShopResponse;
import com.mall.user.service.ShopFollowService;
import com.mall.user.service.ShopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店铺资料与营业状态接口（#49）。服务内部路径不带 /api。
 */
@RestController
@RequestMapping("/shops")
@RequiredArgsConstructor
public class ShopController {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_SELLER = "SELLER";

    private final ShopService shopService;
    private final ShopFollowService shopFollowService;

    /** 卖家查看自己的店铺资料 */
    @GetMapping("/mine")
    public Result<ShopResponse> mine(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireSeller(role);
        return Result.success(shopService.getMine(userId));
    }

    /** 卖家维护店铺名称、头像、简介与联系方式 */
    @PutMapping("/mine")
    public Result<ShopResponse> updateMine(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody ShopRequest request) {
        requireSeller(role);
        return Result.success(shopService.updateMine(userId, request));
    }

    /** 管理员分页查看店铺与营业状态 */
    @GetMapping
    public Result<PageResponse<ShopResponse>> list(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        return Result.success(shopService.list(keyword, status, page, size));
    }

    /** 管理员停用 / 恢复店铺 */
    @PutMapping("/{shopId}/status")
    public Result<ShopResponse> updateStatus(
            @RequestHeader("X-User-Id") String operatorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("shopId") String shopId,
            @Valid @RequestBody ShopStatusRequest request) {
        requireAdmin(role);
        return Result.success(shopService.updateStatus(operatorId, shopId, request));
    }

    /** 按卖家查店铺，供 order-service 下单校验营业状态（服务间直连，不经过网关） */
    @GetMapping("/seller/{sellerId}")
    public Result<ShopResponse> bySeller(@PathVariable("sellerId") String sellerId) {
        return Result.success(shopService.getBySellerId(sellerId));
    }


    /** 店铺主页：公开可读（#142） */
    @GetMapping("/{shopId}")
    public Result<ShopResponse> byId(@PathVariable("shopId") String shopId) {
        return Result.success(shopService.getById(shopId));
    }

    /** 关注店铺（#142）：重复关注幂等，卖家不能关注自己的店 */
    @PostMapping("/{shopId}/follow")
    public Result<Long> follow(@RequestHeader("X-User-Id") String userId,
                               @PathVariable("shopId") String shopId) {
        return Result.success(shopFollowService.follow(userId, shopId));
    }

    /** 取消关注（#142） */
    @DeleteMapping("/{shopId}/follow")
    public Result<Long> unfollow(@RequestHeader("X-User-Id") String userId,
                                 @PathVariable("shopId") String shopId) {
        return Result.success(shopFollowService.unfollow(userId, shopId));
    }

    /** 我关注的店铺（#142） */
    @GetMapping("/following")
    public Result<PageResponse<FollowedShopResponse>> following(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(shopFollowService.listFollowing(userId, page, size));
    }

    private void requireSeller(String role) {
        if (!ROLE_SELLER.equals(role) && !ROLE_ADMIN.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅卖家可维护店铺资料");
        }
    }

    private void requireAdmin(String role) {
        if (!ROLE_ADMIN.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限执行该操作");
        }
    }
}
