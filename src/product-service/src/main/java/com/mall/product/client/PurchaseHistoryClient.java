package com.mall.product.client;

import com.mall.product.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 购买行为客户端（#141）：读最近购买过的商品ID，用于个性化推荐与「已购买不再推荐」。
 *
 * <p>调用失败只降级为「无购买记录」，不阻塞推荐。
 */
@FeignClient(name = "order-service", url = "${services.order-url:}", contextId = "purchaseHistoryClient")
public interface PurchaseHistoryClient {

    /** 秒杀库存到期先由订单服务裁决状态，避免与支付确认同时释放库存。 */
    @org.springframework.web.bind.annotation.PostMapping("/orders/internal/{orderId}/expire-flash-sale")
    Result<Boolean> expireFlashSale(@PathVariable("orderId") String orderId);

    /** 最近购买过的商品ID，按订单创建时间倒序 */
    @GetMapping("/internal/purchases/{userId}")
    Result<List<String>> recentPurchases(@PathVariable("userId") String userId,
                                         @RequestParam("limit") int limit);
}
