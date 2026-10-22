package com.mall.payment.client;

import com.mall.payment.client.dto.OrderDTO;
import com.mall.payment.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 订单服务内部接口。生产环境按服务名从 Nacos 发现，
 * 本地联调可通过 services.order-url 指定直连地址。
 */
@FeignClient(name = "order-service", url = "${services.order-url:}")
public interface OrderClient {

    @GetMapping("/orders/{orderId}")
    Result<OrderDTO> getOrder(@PathVariable("orderId") String orderId,
                              @RequestHeader("X-User-Id") String userId);

    @PostMapping("/orders/{orderId}/paid")
    Result<Void> markPaid(@PathVariable("orderId") String orderId);

    @PostMapping("/orders/{orderId}/refunded")
    Result<Void> markRefunded(@PathVariable("orderId") String orderId);
}
