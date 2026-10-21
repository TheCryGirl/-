package com.mall.order.mapper;

import com.mall.order.entity.Order;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderMapper {

    int insert(Order order);

    Order selectById(@Param("orderId") String orderId);

    List<Order> selectList(@Param("buyerId") String buyerId,
                           @Param("sellerId") String sellerId,
                           @Param("status") String status,
                           @Param("offset") int offset,
                           @Param("size") int size);

    long countList(@Param("buyerId") String buyerId,
                   @Param("sellerId") String sellerId,
                   @Param("status") String status);

    List<String> selectTimeoutCandidates(@Param("deadline") LocalDateTime deadline,
                                         @Param("limit") int limit);

    List<String> selectUnreleasedCancelled(@Param("limit") int limit);

    int markPaid(@Param("orderId") String orderId, @Param("paidAt") LocalDateTime paidAt);

    int markShipped(@Param("orderId") String orderId,
                    @Param("trackingNo") String trackingNo,
                    @Param("updatedAt") LocalDateTime updatedAt);

    int markCompleted(@Param("orderId") String orderId, @Param("updatedAt") LocalDateTime updatedAt);

    int markCancelled(@Param("orderId") String orderId, @Param("updatedAt") LocalDateTime updatedAt);

    int markRefunded(@Param("orderId") String orderId, @Param("updatedAt") LocalDateTime updatedAt);

    int markInventoryReleased(@Param("orderId") String orderId);

    int softDelete(@Param("orderId") String orderId);
}

