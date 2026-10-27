package com.mall.payment.client;

import com.mall.payment.client.dto.ConfirmStockResponse;
import com.mall.payment.client.dto.StockConfirmRequest;
import com.mall.payment.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 商品服务内部接口。
 *
 * <ul>
 *   <li>{@code /confirm} — 支付成功后把预占库存转为实际扣减</li>
 *   <li>{@code /refund-restock} — 退款成功后把已扣减的库存回补（BR-28）</li>
 * </ul>
 */
@FeignClient(name = "product-service", url = "${services.product-url:}")
public interface ProductClient {

    @PostMapping("/products/skus/confirm")
    Result<ConfirmStockResponse> confirmStock(@RequestBody StockConfirmRequest request);

    @PostMapping("/products/skus/refund-restock")
    Result<Void> refundRestock(@RequestBody StockConfirmRequest request);
}
