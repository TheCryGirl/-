package com.mall.user.client;

import com.mall.user.client.dto.ProductBriefDTO;
import com.mall.user.client.dto.SellerVisibilityRequest;
import com.mall.user.client.dto.VisibilitySyncResponse;
import com.mall.user.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 商品服务客户端。
 *
 * <p>收藏夹与足迹的商品标题、价格、状态都从这里取，不跨库 JOIN。
 * 店铺停用 / 恢复时也要通知商品服务同步可见性（#49）。
 */
@FeignClient(name = "product-service", url = "${services.product-url:}")
public interface ProductClient {

    /** 批量取商品简要信息，ids 用英文逗号分隔 */
    @GetMapping("/products/batch")
    Result<List<ProductBriefDTO>> batchGet(@RequestParam("ids") String ids);

    /** 卖家店铺停用 / 恢复后，让商品服务同步该卖家商品的可见性 */
    @PostMapping("/products/internal/seller-visibility")
    Result<VisibilitySyncResponse> syncSellerVisibility(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestBody SellerVisibilityRequest request);
}
