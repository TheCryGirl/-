package com.mall.cart.client;

import com.mall.cart.client.dto.ProductDetailDTO;
import com.mall.cart.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 商品服务客户端。购物车展示时实时取价格、标题与库存（契约 4.4）。
 */
@FeignClient(name = "product-service", url = "${services.product-url:}")
public interface ProductClient {

    @GetMapping("/products/{productId}")
    Result<ProductDetailDTO> getProduct(@PathVariable("productId") String productId,
                                        @RequestHeader(value = "X-Internal-Token", required = false) String internalToken);
}
