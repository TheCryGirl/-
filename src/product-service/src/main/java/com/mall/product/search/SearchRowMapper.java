package com.mall.product.search;

import com.mall.product.common.ProductStatus;
import com.mall.product.dto.response.ProductListItem;
import com.mall.product.dto.response.ProductSearchRow;

/** 检索 / 推荐行转换为对外列表项（#141）。 */
public final class SearchRowMapper {

    private SearchRowMapper() {
    }

    public static ProductListItem toListItem(ProductSearchRow row) {
        ProductListItem item = new ProductListItem();
        item.setProductId(row.getProductId());
        item.setSellerId(row.getSellerId());
        item.setTitle(row.getTitle());
        item.setSubtitle(row.getSubtitle());
        item.setMainImage(row.getMainImage());
        item.setCategoryId(row.getCategoryId());
        item.setStatus(row.getStatus() == null ? null : ProductStatus.valueOf(row.getStatus()));
        item.setPrice(row.getPrice() == null ? 0L : row.getPrice());
        item.setStock(row.getStock() == null ? 0 : row.getStock());
        item.setCreatedAt(row.getCreatedAt());
        return item;
    }
}
