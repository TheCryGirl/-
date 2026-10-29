package com.mall.user.mapper;

import com.mall.user.entity.Shop;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 店铺数据访问层。
 */
@Mapper
public interface ShopMapper {

    int insert(Shop shop);

    int updateInfo(Shop shop);

    int updateStatus(@Param("shopId") String shopId, @Param("status") String status);

    Shop selectById(@Param("shopId") String shopId);

    Shop selectBySellerId(@Param("sellerId") String sellerId);

    List<Shop> selectPage(@Param("keyword") String keyword,
                          @Param("status") String status,
                          @Param("offset") int offset,
                          @Param("size") int size);

    long countPage(@Param("keyword") String keyword, @Param("status") String status);
}
