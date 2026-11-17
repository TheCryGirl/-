package com.mall.user.mapper;

import com.mall.user.entity.TicketMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 工单消息数据访问层（#142）。 */
@Mapper
public interface TicketMessageMapper {

    @Insert("INSERT INTO ticket_message (ticket_id, sender_id, sender_role, content, created_at) "
            + "VALUES (#{ticketId}, #{senderId}, #{senderRole}, #{content}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TicketMessage message);

    @Select("SELECT * FROM ticket_message WHERE ticket_id = #{ticketId} ORDER BY id ASC")
    List<TicketMessage> selectByTicket(@Param("ticketId") String ticketId);
}