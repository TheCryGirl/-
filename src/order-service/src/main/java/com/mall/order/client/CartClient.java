package com.mall.order.client;

import com.mall.order.client.dto.CartDTO;
import com.mall.order.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "cart-service", url = "${services.cart-url:}")
public interface CartClient {

    @GetMapping("/internal/cart")
    Result<CartDTO> getCart(@RequestHeader("X-Internal-Token") String internalToken,
                            @RequestHeader("X-User-Id") String userId);

    @DeleteMapping("/internal/cart/items/{cartItemId}")
    Result<Void> deleteItem(@RequestHeader("X-Internal-Token") String internalToken,
                            @RequestHeader("X-User-Id") String userId,
                            @PathVariable("cartItemId") String cartItemId);
}
