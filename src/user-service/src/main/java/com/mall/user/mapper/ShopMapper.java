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

    /** 卖家提交入驻申请：店名、联系方式、资质与审核状态一起写入 */
    int updateApply(Shop shop);

    /** 管理员审核：审核状态、营业状态与保证金一起生效 */
    int updateAudit(@Param("shopId") String shopId,
                    @Param("applyStatus") String applyStatus,
                    @Param("status") String status,
                    @Param("depositAmount") Long depositAmount);

    Shop selectById(@Param("shopId") String shopId);

    Shop lockById(@Param("shopId") String shopId);

    /** 运费模板挂到店铺上（#136） */
    int updateFreightTemplateId(@Param("shopId") String shopId,
                                @Param("templateId") String templateId,
                                @Param("updatedAt") java.time.LocalDateTime updatedAt);

    Shop selectBySellerId(@Param("sellerId") String sellerId);

    List<Shop> selectPage(@Param("keyword") String keyword,
                          @Param("status") String status,
                          @Param("applyStatus") String applyStatus,
                          @Param("offset") int offset,
                          @Param("size") int size);

    long countPage(@Param("keyword") String keyword,
                   @Param("status") String status,
                   @Param("applyStatus") String applyStatus);
}
