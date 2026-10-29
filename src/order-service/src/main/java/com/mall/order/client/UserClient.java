package com.mall.order.client;

import com.mall.order.client.dto.AddressDTO;
import com.mall.order.client.dto.ShopBriefDTO;
import com.mall.order.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "user-service", url = "${services.user-url:}")
public interface UserClient {

    /**
     * 查询用户收货地址。
     *
     * <p>user-service 的地址接口用 {@code X-User-Id} 校验「只能查自己的地址」，
     * 内部调用必须把这个头一并传过去，否则会收到「缺少请求头: X-User-Id」。
     */
    @GetMapping("/users/{userId}/addresses")
    Result<List<AddressDTO>> addresses(@PathVariable("userId") String userId,
                                      @RequestHeader("X-User-Id") String currentUserId);

    /** 卖家店铺营业状态，供下单前校验（#49） */
    @GetMapping("/shops/seller/{sellerId}")
    Result<ShopBriefDTO> shopBySeller(@PathVariable("sellerId") String sellerId);
}