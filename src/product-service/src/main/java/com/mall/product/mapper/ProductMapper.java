package com.mall.product.mapper;

import com.mall.product.common.ProductStatus;
import com.mall.product.dto.response.ProductBriefResponse;
import com.mall.product.dto.response.ProductListItem;
import com.mall.product.entity.Product;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ProductMapper {

    int insert(Product product);

    int updateInfo(Product product);

    int updateStatus(@Param("productId") String productId,
                     @Param("status") ProductStatus status,
                     @Param("updatedBy") String updatedBy);

    int softDelete(@Param("productId") String productId, @Param("updatedBy") String updatedBy);

    Product selectById(@Param("productId") String productId);

    List<ProductListItem> selectPublicList(@Param("keyword") String keyword,
                                           @Param("categoryId") String categoryId,
                                           @Param("minPrice") Long minPrice,
                                           @Param("maxPrice") Long maxPrice,
                                           @Param("sort") String sort,
                                           @Param("offset") int offset,
                                           @Param("size") int size);

    long countPublic(@Param("keyword") String keyword,
                     @Param("categoryId") String categoryId,
                     @Param("minPrice") Long minPrice,
                     @Param("maxPrice") Long maxPrice);

    List<ProductListItem> selectAdminList(@Param("status") String status,
                                          @Param("offset") int offset,
                                          @Param("size") int size);

    long countAdmin(@Param("status") String status);

    List<ProductListItem> selectMine(@Param("sellerId") String sellerId,
                                     @Param("status") String status,
                                     @Param("offset") int offset,
                                     @Param("size") int size);

    long countMine(@Param("sellerId") String sellerId, @Param("status") String status);

    int incrementSales(@Param("productId") String productId, @Param("quantity") int quantity);

    /** 批量取商品简要信息，供收藏夹与足迹使用；不按状态过滤，失效商品也要能展示 */
    List<ProductBriefResponse> selectBriefByIds(@Param("productIds") List<String> productIds);

    /** 店铺停用：在售商品转 SUSPENDED */
    int suspendBySellerId(@Param("sellerId") String sellerId, @Param("updatedBy") String updatedBy);

    /** 店铺恢复：被停用而挂起的商品恢复在售，卖家自己下架的不受影响 */
    int resumeBySellerId(@Param("sellerId") String sellerId, @Param("updatedBy") String updatedBy);
}
