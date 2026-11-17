package com.mall.payment.mapper;

import com.mall.payment.entity.AfterSaleAttachment;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 售后凭证附件数据访问（#143）。
 */
public interface AfterSaleAttachmentMapper {

    int insert(AfterSaleAttachment attachment);

    List<AfterSaleAttachment> selectByTicketId(@Param("ticketId") String ticketId);

    int countByTicketId(@Param("ticketId") String ticketId);
}