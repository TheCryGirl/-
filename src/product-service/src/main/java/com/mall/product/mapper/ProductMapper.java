package com.mall.product.mapper;

import com.mall.product.common.ProductStatus;
import com.mall.product.dto.response.ProductBriefResponse;
import com.mall.product.dto.response.ProductListItem;
import com.mall.product.dto.response.ProductSearchRow;
import com.mall.product.dto.response.ProductSpecRow;
import com.mall.product.entity.Product;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ProductMapper {

    int insert(Product product);

    int updateInfo(Product product);

    int updateStatus(@Param("productId") String productId,
                     @Param("status") ProductStatus status,
                     @Param("updatedBy") String updatedBy);

    int updateSellerStatus(@Param("productId") String productId,
                           @Param("expectedStatus") ProductStatus expectedStatus,
                           @Param("status") ProductStatus status,
                           @Param("updatedBy") String updatedBy);

    int softDelete(@Param("productId") String productId, @Param("updatedBy") String updatedBy);

    Product selectById(@Param("productId") String productId);

    List<ProductListItem> selectPublicList(@Param("keyword") String keyword,
                                           @Param("categoryId") String categoryId,
                              @Param("sellerId") String sellerId,
                                           @Param("minPrice") Long minPrice,
                                           @Param("maxPrice") Long maxPrice,
                                           @Param("stockScope") String stockScope,
                                           @Param("sort") String sort,
                                           @Param("offset") int offset,
                                           @Param("size") int size);

    long countPublic(@Param("keyword") String keyword,
                     @Param("categoryId") String categoryId,
                     @Param("sellerId") String sellerId,
                     @Param("minPrice") Long minPrice,
                     @Param("maxPrice") Long maxPrice,
                     @Param("stockScope") String stockScope);

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

    /* ---------------- #141 检索与推荐 ---------------- */

    /**
     * 检索候选集：在售商品 + 分类名 + 最低价 + 库存合计。
     *
     * <p>关键词不进 SQL：分词、多字段匹配与相关度评分在 Java 侧完成（见 ProductSearchEngine），
     * 这样规格名、分类名才能与标题一起参与打分，也不必依赖数据库的中文分词能力。
     *
     * @param stockScope IN 仅有货 / OUT 仅无货 / null 不限
     */
    List<ProductSearchRow> selectSearchRows(@Param("categoryId") String categoryId,
                                            @Param("minPrice") Long minPrice,
                                            @Param("maxPrice") Long maxPrice,
                                            @Param("stockScope") String stockScope);

    /** 全部在售商品的 SKU 规格名，供规格名匹配与纠错词表使用 */
    List<ProductSpecRow> selectSpecNames();

    /** 指定分类下的热销商品，排除已购买 / 已推荐的商品 */
    List<ProductSearchRow> selectRecommendRows(@Param("categoryIds") List<String> categoryIds,
                                               @Param("excludeIds") List<String> excludeIds,
                                               @Param("limit") int limit);

    /** 全站热销，用于冷启动与补位 */
    List<ProductSearchRow> selectHotRows(@Param("excludeIds") List<String> excludeIds,
                                         @Param("limit") int limit);
}
