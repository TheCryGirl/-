package com.mall.user.mapper;

import com.mall.user.entity.BrowseHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 浏览足迹数据访问层。同一用户同一商品只保留一条记录，浏览时更新 visited_at。
 */
@Mapper
public interface BrowseHistoryMapper {

    int insert(BrowseHistory history);

    int updateVisitedAt(@Param("userId") String userId,
                        @Param("productId") String productId,
                        @Param("visitedAt") LocalDateTime visitedAt);

    BrowseHistory selectByUserAndProduct(@Param("userId") String userId, @Param("productId") String productId);

    List<BrowseHistory> selectRecent(@Param("userId") String userId, @Param("limit") int limit);

    int deleteByUserAndProduct(@Param("userId") String userId, @Param("productId") String productId);

    int deleteAllByUserId(@Param("userId") String userId);
}
