package com.mall.user.mapper;

import com.mall.user.entity.Ticket;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** 客服工单数据访问层（#142）。 */
@Mapper
public interface TicketMapper {

    @Insert("INSERT INTO ticket (ticket_id, ticket_no, user_id, type, title, status, assignee_id, biz_id, "
            + "created_at, updated_at, resolved_at) VALUES (#{ticketId}, #{ticketNo}, #{userId}, #{type}, #{title}, "
            + "#{status}, #{assigneeId}, #{bizId}, #{createdAt}, #{updatedAt}, #{resolvedAt})")
    int insert(Ticket ticket);

    @Select("SELECT * FROM ticket WHERE ticket_id = #{ticketId}")
    Ticket selectById(@Param("ticketId") String ticketId);

    @Select("<script>SELECT * FROM ticket WHERE 1 = 1 "
            + "<if test='userId != null'>AND user_id = #{userId} </if>"
            + "<if test='status != null'>AND status = #{status} </if>"
            + "ORDER BY created_at DESC, ticket_id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<Ticket> selectPage(@Param("userId") String userId,
                            @Param("status") String status,
                            @Param("offset") int offset,
                            @Param("size") int size);

    @Select("<script>SELECT COUNT(*) FROM ticket WHERE 1 = 1 "
            + "<if test='userId != null'>AND user_id = #{userId} </if>"
            + "<if test='status != null'>AND status = #{status}</if></script>")
    long countPage(@Param("userId") String userId, @Param("status") String status);

    @Update("UPDATE ticket SET status = #{status}, assignee_id = #{assigneeId}, resolved_at = #{resolvedAt}, "
            + "updated_at = NOW() WHERE ticket_id = #{ticketId}")
    int updateStatus(@Param("ticketId") String ticketId,
                     @Param("status") String status,
                     @Param("assigneeId") String assigneeId,
                     @Param("resolvedAt") LocalDateTime resolvedAt);

    @Update("UPDATE ticket SET updated_at = NOW() WHERE ticket_id = #{ticketId}")
    int touch(@Param("ticketId") String ticketId);
}