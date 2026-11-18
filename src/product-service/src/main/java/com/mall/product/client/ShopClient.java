package com.mall.product.client;

import com.mall.product.client.dto.ShopBriefDTO;
import com.mall.product.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 用户服务里的店铺信息客户端（#49）。
 *
 * <p>用于审核与卖家上架前确认店铺是否被停用，避免停用期间商品被放回在售。
 */
@FeignClient(name = "user-service", url = "${services.user-url:}")
public interface ShopClient {

    /** 按店铺ID取店铺信息，供店铺主页按 shopId 过滤商品（#142）。 */
    @GetMapping("/shops/{shopId}")
    Result<ShopBriefDTO> getByShopId(@PathVariable("shopId") String shopId);

    @GetMapping("/shops/internal/publication/{sellerId}")
    Result<PublicationEligibility> publication(@PathVariable("sellerId") String sellerId);

    record PublicationEligibility(boolean publishable, String reason) {}
}
