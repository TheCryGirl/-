package com.mall.user.mapper;

import com.mall.user.entity.ShopApplyLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 店铺入驻审核流水数据访问层（#144）。
 */
@Mapper
public interface ShopApplyLogMapper {

    int insert(ShopApplyLog log);

    /** 按时间正序返回某店铺的审核流水，用于追溯 */
    List<ShopApplyLog> selectByShopId(@Param("shopId") String shopId);

    /** 最近一次驳回记录，卖家侧展示驳回理由用 */
    ShopApplyLog selectLatestReject(@Param("shopId") String shopId);

    /** 累计提交申请的次数（含首次），用于防刷上限（#144） */
    int countApplySubmissions(@Param("shopId") String shopId);
}
