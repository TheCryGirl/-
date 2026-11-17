package com.mall.payment.mapper;

import com.mall.payment.entity.AfterSaleEvent;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 售后进度时间线数据访问（#143）。按时间正序读取。
 */
public interface AfterSaleEventMapper {

    int insert(AfterSaleEvent event);

    List<AfterSaleEvent> selectByTicketId(@Param("ticketId") String ticketId);
}