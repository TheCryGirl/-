package com.mall.order.mapper;

import com.mall.order.entity.OrderLogisticsTrack;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OrderLogisticsTrackMapper {

    int insert(OrderLogisticsTrack track);

    /** 按发生时间正序返回整单轨迹，时间相同按主键稳定排序 */
    List<OrderLogisticsTrack> selectByOrderId(@Param("orderId") String orderId);

    long countByOrderId(@Param("orderId") String orderId);

    int deleteByOrderId(@Param("orderId") String orderId);
}
