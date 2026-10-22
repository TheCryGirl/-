package com.mall.order.mapper;

import com.mall.order.entity.OrderCompensation;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CompensationMapper {

    int insert(OrderCompensation task);

    OrderCompensation selectByOrderId(@Param("orderId") String orderId);

    List<OrderCompensation> selectDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    int markDone(@Param("id") Long id, @Param("now") LocalDateTime now);

    int scheduleRetry(@Param("id") Long id,
                      @Param("retryCount") int retryCount,
                      @Param("nextRetryAt") LocalDateTime nextRetryAt,
                      @Param("lastError") String lastError,
                      @Param("now") LocalDateTime now);

    int markDead(@Param("id") Long id, @Param("lastError") String lastError, @Param("now") LocalDateTime now);
}
