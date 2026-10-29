package com.mall.order.mapper;

import com.mall.order.dto.response.EvaluationStatsResponse;
import com.mall.order.entity.OrderEvaluation;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OrderEvaluationMapper {

    int insert(OrderEvaluation evaluation);

    OrderEvaluation selectByOrderId(@Param("orderId") String orderId);

    OrderEvaluation selectById(@Param("evaluationId") String evaluationId);

    List<OrderEvaluation> selectByProduct(@Param("productId") String productId,
                                          @Param("offset") int offset,
                                          @Param("size") int size);

    long countByProduct(@Param("productId") String productId);

    EvaluationStatsResponse selectProductStats(@Param("productId") String productId);

    List<OrderEvaluation> selectAdminPage(@Param("status") String status,
                                          @Param("productId") String productId,
                                          @Param("offset") int offset,
                                          @Param("size") int size);

    long countAdmin(@Param("status") String status, @Param("productId") String productId);

    int updateStatus(@Param("evaluationId") String evaluationId, @Param("status") String status);
}
