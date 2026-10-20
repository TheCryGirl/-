package com.mall.payment.client;

import com.mall.payment.client.dto.StockConfirmRequest;
import com.mall.payment.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 商品服务内部接口：支付成功后把预占库存转为实际扣减。
 */
@FeignClient(name = "product-service", url = "${services.product-url:}")
public interface ProductClient {

    @PostMapping("/products/skus/confirm")
    Result<Void> confirmStock(@RequestBody StockConfirmRequest request);
}
