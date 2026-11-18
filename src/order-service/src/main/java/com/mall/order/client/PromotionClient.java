package com.mall.order.client;

import com.mall.order.client.dto.PromotionDTO;
import com.mall.order.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

/**
 * 满减/满折活动内部接口客户端（#140）：计价链读取当前生效中的活动。
 */
@FeignClient(name = "payment-service", url = "${services.payment-url:}", contextId = "promotionClient")
public interface PromotionClient {

    @GetMapping("/internal/promotions/active")
    Result<List<PromotionDTO>> active(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken);
}
