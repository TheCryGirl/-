package com.mall.product.mapper;

import com.mall.product.common.ReservationStatus;
import com.mall.product.entity.StockReservation;
import com.mall.product.entity.StockReservationItem;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StockReservationMapper {

    int insertReservation(StockReservation reservation);

    int insertItem(StockReservationItem item);

    StockReservation selectByOrderId(@Param("orderId") String orderId);

    List<StockReservationItem> selectItems(@Param("reservationId") String reservationId);

    List<String> selectExpiredOrderIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    int updateStatus(@Param("orderId") String orderId,
                     @Param("from") ReservationStatus from,
                     @Param("to") ReservationStatus to,
                     @Param("updatedAt") LocalDateTime updatedAt);
}
