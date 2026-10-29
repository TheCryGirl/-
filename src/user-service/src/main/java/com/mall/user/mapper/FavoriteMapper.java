package com.mall.user.mapper;

import com.mall.user.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 收藏夹数据访问层。唯一索引 uk_favorite_user_product 保证重复收藏幂等。
 */
@Mapper
public interface FavoriteMapper {

    int insert(Favorite favorite);

    int deleteByUserAndProduct(@Param("userId") String userId, @Param("productId") String productId);

    Favorite selectByUserAndProduct(@Param("userId") String userId, @Param("productId") String productId);

    List<Favorite> selectPage(@Param("userId") String userId,
                              @Param("offset") int offset,
                              @Param("size") int size);

    long countByUserId(@Param("userId") String userId);
}
