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

    /** 渠道异步退款：先挂退款单号，不改售后状态；退款成功回调再置 REFUNDED */
    int attachRefundId(@Param("ticketId") String ticketId, @Param("refundId") String refundId);

    /** 渠道退款成功后按退款单号同步售后单为 REFUNDED（条件更新，幂等） */
    int markRefundedByRefundId(@Param("refundId") String refundId,
                               @Param("now") LocalDateTime now);

    /** #143 买家撤销：PENDING -> CANCELLED，终态置 active_flag=NULL */
    int markCancelled(@Param("ticketId") String ticketId,
                      @Param("buyerId") String buyerId,
                      @Param("remark") String remark,
                      @Param("now") LocalDateTime now);

    /** #143 买家申请仲裁：REJECTED -> ARBITRATING，重新置 active_flag=1（仲裁期间锁定） */
    int markArbitrating(@Param("ticketId") String ticketId,
                        @Param("buyerId") String buyerId,
                        @Param("remark") String remark,
                        @Param("now") LocalDateTime now);

    /** #143 管理员裁定支持退款：ARBITRATING -> REFUNDED，挂退款单号 */
    int markArbitrationRefunded(@Param("ticketId") String ticketId,
                                @Param("refundId") String refundId,
                                @Param("operatorId") String operatorId,
                                @Param("remark") String remark,
                                @Param("now") LocalDateTime now);

    /** #143 管理员裁定驳回：ARBITRATING -> REJECTED */
    int markArbitrationRejected(@Param("ticketId") String ticketId,
                                @Param("operatorId") String operatorId,
                                @Param("remark") String remark,
                                @Param("now") LocalDateTime now);

    /** #143 按退款单号查售后单，用于渠道异步退款成功时补记时间线 */
    AfterSaleTicket selectByRefundId(@Param("refundId") String refundId);
}
