package com.mall.cart.controller;

import com.mall.cart.common.Result;
import com.mall.cart.dto.request.AddCartItemRequest;
import com.mall.cart.dto.request.UpdateQuantityRequest;
import com.mall.cart.dto.request.UpdateSelectedRequest;
import com.mall.cart.dto.response.CartResponse;
import com.mall.cart.service.CartService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 购物车接口。同时兼容网关对外路径 /api/cart 与服务内部路径 /cart，
 * 后者供 order-service 直接调用。
 */
@RestController
@RequestMapping({"/cart", "/api/cart"})
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public Result<CartResponse> getCart(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return Result.success(cartService.getCart(userId));
    }

    @PostMapping("/items")
    public Result<Void> addItem(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody AddCartItemRequest request) {
        cartService.addCartItem(userId, request);
        return Result.success();
    }

    @PutMapping("/items/{cartItemId}")
    public Result<Void> updateQuantity(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String cartItemId,
            @Valid @RequestBody UpdateQuantityRequest request) {
        cartService.updateQuantity(userId, cartItemId, request.getQuantity());
        return Result.success();
    }

    @PutMapping("/items/{cartItemId}/select")
    public Result<Void> updateSelected(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String cartItemId,
            @Valid @RequestBody UpdateSelectedRequest request) {
        cartService.updateSelected(userId, cartItemId, request.getSelected());
        return Result.success();
    }

    @DeleteMapping("/items/{cartItemId}")
    public Result<Void> removeItem(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String cartItemId) {
        cartService.removeItem(userId, cartItemId);
        return Result.success();
    }

    @DeleteMapping
    public Result<Void> clearCart(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        cartService.clearCart(userId);
        return Result.success();
    }
}
