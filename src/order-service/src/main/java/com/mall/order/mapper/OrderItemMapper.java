package com.mall.order.mapper;

import com.mall.order.entity.OrderItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OrderItemMapper {

    int insertBatch(@Param("items") List<OrderItem> items);

    List<OrderItem> selectByOrderId(@Param("orderId") String orderId);

    int softDeleteByOrderId(@Param("orderId") String orderId);
}
