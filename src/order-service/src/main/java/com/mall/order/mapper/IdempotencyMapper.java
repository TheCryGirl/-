package com.mall.order.mapper;

import com.mall.order.entity.OrderIdempotency;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface IdempotencyMapper {

    int insert(OrderIdempotency record);

    OrderIdempotency selectByKey(@Param("idemKey") String idemKey);

    int markSuccess(@Param("idemKey") String idemKey,
                    @Param("orderId") String orderId,
                    @Param("updatedAt") LocalDateTime updatedAt);

    int deleteByKey(@Param("idemKey") String idemKey);

    int deleteStale(@Param("cutoff") LocalDateTime cutoff);
}
