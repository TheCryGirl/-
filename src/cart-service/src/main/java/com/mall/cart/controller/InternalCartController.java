package com.mall.cart.controller;

import com.mall.cart.common.Result;
import com.mall.cart.config.InternalTokenVerifier;
import com.mall.cart.dto.response.CartResponse;
import com.mall.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供 order-service 直接调用的内部接口，不经过网关。
 */
@RestController
@RequestMapping("/internal/cart")
@RequiredArgsConstructor
public class InternalCartController {

    private final CartService cartService;
    private final InternalTokenVerifier internalTokenVerifier;

    @GetMapping
    public Result<CartResponse> getCart(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        internalTokenVerifier.verify(token);
        return Result.success(cartService.getCart(userId));
    }

    @DeleteMapping("/items/{cartItemId}")
    public Result<Void> removeItem(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable String cartItemId) {
        internalTokenVerifier.verify(token);
        cartService.removeItem(userId, cartItemId);
        return Result.success();
    }
}
