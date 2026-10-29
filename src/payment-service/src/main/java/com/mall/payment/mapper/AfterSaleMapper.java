package com.mall.payment.mapper;

import com.mall.payment.common.AfterSaleStatus;
import com.mall.payment.common.AfterSaleType;
import com.mall.payment.entity.AfterSaleTicket;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 售后单数据访问。状态流转全部是带前后状态的条件 UPDATE。
 */
public interface AfterSaleMapper {

    int insert(AfterSaleTicket ticket);

    AfterSaleTicket selectById(@Param("ticketId") String ticketId);

    AfterSaleTicket selectByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    AfterSaleTicket selectActiveByOrderId(@Param("orderId") String orderId);

    List<AfterSaleTicket> selectPage(@Param("status") AfterSaleStatus status,
                                     @Param("afterSaleType") AfterSaleType afterSaleType,
                                     @Param("orderId") String orderId,
                                     @Param("buyerId") String buyerId,
                                     @Param("sellerId") String sellerId,
                                     @Param("offset") int offset,
                                     @Param("size") int size);

    long count(@Param("status") AfterSaleStatus status,
               @Param("afterSaleType") AfterSaleType afterSaleType,
               @Param("orderId") String orderId,
               @Param("buyerId") String buyerId,
               @Param("sellerId") String sellerId);

    int markApproved(@Param("ticketId") String ticketId,
                     @Param("sellerId") String sellerId,
                     @Param("auditedBy") String auditedBy,
                     @Param("remark") String remark,
                     @Param("now") LocalDateTime now);

    int markRejected(@Param("ticketId") String ticketId,
                     @Param("sellerId") String sellerId,
                     @Param("auditedBy") String auditedBy,
                     @Param("remark") String remark,
                     @Param("now") LocalDateTime now);

    int markBuyerShipped(@Param("ticketId") String ticketId,
                         @Param("buyerId") String buyerId,
                         @Param("trackingNo") String trackingNo,
                         @Param("now") LocalDateTime now);

    int markSellerReceived(@Param("ticketId") String ticketId,
                           @Param("sellerId") String sellerId,
                           @Param("remark") String remark,
                           @Param("now") LocalDateTime now);

    int markRefundOnlyRefunded(@Param("ticketId") String ticketId,
                               @Param("sellerId") String sellerId,
                               @Param("refundId") String refundId,
                               @Param("remark") String remark,
                               @Param("now") LocalDateTime now);

    int markReturnRefunded(@Param("ticketId") String ticketId,
                           @Param("sellerId") String sellerId,
                           @Param("refundId") String refundId,
                           @Param("remark") String remark,
                           @Param("now") LocalDateTime now);

    int markExchangeCompleted(@Param("ticketId") String ticketId,
                              @Param("sellerId") String sellerId,
                              @Param("trackingNo") String trackingNo,
                              @Param("remark") String remark,
                              @Param("now") LocalDateTime now);
}
