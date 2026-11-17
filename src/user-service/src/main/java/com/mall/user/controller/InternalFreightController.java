package com.mall.user.controller;

import com.mall.user.common.Result;
import com.mall.user.dto.response.FreightConfigResponse;
import com.mall.user.service.FreightService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部接口：按卖家取运费模板（#136），供 order-service 计算运费。
 *
 * <p>ServiceIngressFilter 已对整个服务校验 X-Internal-Token，这里不再重复验签。
 */
@RestController
@RequiredArgsConstructor
public class InternalFreightController {

    private final FreightService freightService;

    @GetMapping("/internal/shops/{sellerId}/freight")
    public Result<FreightConfigResponse> bySeller(@PathVariable("sellerId") String sellerId) {
        return Result.success(freightService.getBySellerId(sellerId));
    }
}
