package com.mall.product.mapper;

import com.mall.product.entity.Sku;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SkuMapper {

    int insert(Sku sku);

    Sku selectById(@Param("skuId") String skuId);

    List<Sku> selectByProductId(@Param("productId") String productId);

    int updateStockAvailable(@Param("skuId") String skuId,
                             @Param("stockAvailable") int stockAvailable,
                             @Param("updatedBy") String updatedBy);

    /**
     * 条件 UPDATE：判断与扣减在同一条 SQL 中完成。
     * 返回 0 表示库存不足或商品不在售。
     */
    int reserve(@Param("skuId") String skuId, @Param("quantity") int quantity);

    int restore(@Param("skuId") String skuId, @Param("quantity") int quantity);

    int confirm(@Param("skuId") String skuId, @Param("quantity") int quantity);

    int softDeleteByProductId(@Param("productId") String productId, @Param("updatedBy") String updatedBy);
}
