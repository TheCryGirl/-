package com.mall.order.client;

import com.mall.order.client.dto.AddressDTO;
import com.mall.order.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "user-service", url = "${services.user-url:}")
public interface UserClient {

    @GetMapping("/users/{userId}/addresses")
    Result<List<AddressDTO>> addresses(@PathVariable("userId") String userId);
}
