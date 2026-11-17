package com.mall.user.mapper;

import com.mall.user.entity.ShopFollow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 店铺关注数据访问层（#142）。 */
@Mapper
public interface ShopFollowMapper {

    @Insert("INSERT IGNORE INTO shop_follow (follow_id, shop_id, user_id, created_at) "
            + "VALUES (#{followId}, #{shopId}, #{userId}, #{createdAt})")
    int insert(ShopFollow follow);

    @Delete("DELETE FROM shop_follow WHERE shop_id = #{shopId} AND user_id = #{userId}")
    int delete(@Param("shopId") String shopId, @Param("userId") String userId);

    @Select("SELECT COUNT(*) FROM shop_follow WHERE shop_id = #{shopId} AND user_id = #{userId}")
    long countByUserAndShop(@Param("shopId") String shopId, @Param("userId") String userId);

    @Select("SELECT COUNT(*) FROM shop_follow WHERE shop_id = #{shopId}")
    long countByShop(@Param("shopId") String shopId);

    @Select("SELECT f.follow_id AS followId, f.shop_id AS shopId, f.user_id AS userId, f.created_at AS createdAt, "
            + "s.name AS name, s.logo AS logo, s.description AS description, s.status AS status, s.seller_id AS sellerId "
            + "FROM shop_follow f JOIN shop s ON s.shop_id = f.shop_id AND s.is_deleted = 0 "
            + "WHERE f.user_id = #{userId} ORDER BY f.created_at DESC LIMIT #{size} OFFSET #{offset}")
    List<Map<String, Object>> selectFollowing(@Param("userId") String userId,
                                              @Param("offset") int offset,
                                              @Param("size") int size);

    @Select("SELECT COUNT(*) FROM shop_follow f JOIN shop s ON s.shop_id = f.shop_id AND s.is_deleted = 0 "
            + "WHERE f.user_id = #{userId}")
    long countFollowing(@Param("userId") String userId);
}