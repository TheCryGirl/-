package com.mall.cart.service;

import com.mall.cart.dto.request.AddCartItemRequest;
import com.mall.cart.dto.response.CartResponse;

/**
 * 购物车业务接口。
 */
public interface CartService {

    CartResponse getCart(String userId);

    void addCartItem(String userId, AddCartItemRequest request);

    void updateQuantity(String userId, String cartItemId, Integer quantity);

    void updateSelected(String userId, String cartItemId, Boolean selected);

    void removeItem(String userId, String cartItemId);

    void clearCart(String userId);
}
