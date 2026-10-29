package com.mall.product.dto.response;

import com.mall.product.common.ProductStatus;
import lombok.Data;

/**
 * 商品简要信息，供 user-service 的收藏夹与浏览足迹组装展示（#52）。
 *
 * <p>不包含 SKU 明细，只给展示与失效判断需要的字段。
 */
@Data
public class ProductBriefResponse {

    private String productId;
    private String sellerId;
    private String title;
    private String mainImage;
    private String categoryId;
    private ProductStatus status;
    private Long price;
}
