package com.mall.order.client;

import com.mall.order.client.dto.ProductDetailDTO;
import com.mall.order.client.dto.ReleaseResponseDTO;
import com.mall.order.client.dto.ReserveStockRequestDTO;
import com.mall.order.client.dto.ReserveStockResponseDTO;
import com.mall.order.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "product-service", url = "${services.product-url:}")
public interface ProductClient {

    @GetMapping("/products/{productId}")
    Result<ProductDetailDTO> getProduct(@PathVariable("productId") String productId);

    @PostMapping("/products/skus/reserve")
    Result<ReserveStockResponseDTO> reserve(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestBody ReserveStockRequestDTO request);

    @PostMapping("/products/skus/release")
    Result<ReleaseResponseDTO> release(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestBody ReleaseRequest request);

    record ReleaseRequest(String orderId) {
    }
}
