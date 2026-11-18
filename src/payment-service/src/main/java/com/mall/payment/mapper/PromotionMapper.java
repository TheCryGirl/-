package com.mall.payment.mapper;

import com.mall.payment.common.PromotionStatus;
import com.mall.payment.entity.Promotion;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 满减/满折活动数据访问（#140）。
 */
public interface PromotionMapper {

    int insert(Promotion promotion);

    Promotion selectById(@Param("promotionId") String promotionId);

    /** 计价链试算用：当前生效中的活动 */
    List<Promotion> selectActive(@Param("now") LocalDateTime now);

    List<Promotion> selectPage(@Param("status") PromotionStatus status,
                               @Param("offset") int offset, @Param("size") int size);

    long count(@Param("status") PromotionStatus status);

    int updateStatus(@Param("promotionId") String promotionId,
                     @Param("status") PromotionStatus status,
                     @Param("updatedBy") String updatedBy);
}