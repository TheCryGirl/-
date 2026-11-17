package com.mall.order.client;

import com.mall.order.client.dto.FreightConfigDTO;
import com.mall.order.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 店铺运费模板客户端（#136）：配置在 user-service，计算在 order-service。
 */
@FeignClient(name = "user-service", url = "${services.user-url:}", contextId = "freightClient")
public interface FreightClient {

    /** 按卖家取运费模板；没有配置过模板时返回 freeThreshold=0 且规则为空 */
    @GetMapping("/internal/shops/{sellerId}/freight")
    Result<FreightConfigDTO> bySeller(@PathVariable("sellerId") String sellerId);
}
