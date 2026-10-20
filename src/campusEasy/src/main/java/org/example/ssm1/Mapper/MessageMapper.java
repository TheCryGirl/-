package org.example.ssm1.Mapper;

import org.apache.ibatis.annotations.*;
import org.example.ssm1.Entity.Message;
import java.util.List;

@Mapper
public interface MessageMapper {

    @Insert("INSERT INTO message (sender_id, receiver_id, content, create_time, is_read) VALUES (#{senderId}, #{receiverId}, #{content}, #{createTime}, #{isRead})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Message message);

    @Select("SELECT id, sender_id as senderId, receiver_id as receiverId, content, create_time as createTime, is_read as isRead FROM message WHERE (sender_id = #{userId1} AND receiver_id = #{userId2}) OR (sender_id = #{userId2} AND receiver_id = #{userId1}) ORDER BY id ASC")
    List<Message> getConversation(@Param("userId1") String userId1, @Param("userId2") String userId2);

    @Select("SELECT DISTINCT sender_id FROM message WHERE receiver_id = #{userId} UNION SELECT DISTINCT receiver_id FROM message WHERE sender_id = #{userId}")
    List<String> getChatPartners(String userId);

    @Update("UPDATE message SET is_read = 1 WHERE message_id = #{message_id}")
    int updateIsRead(@Param("message_id") Long message_id);

    @Select("SELECT id, sender_id as senderId, receiver_id as receiverId, content, create_time as createTime, is_read as isRead FROM message WHERE sender_id = #{from} AND receiver_id = #{to} ORDER BY id ASC")
    List<Message> getMessages(@Param("from") String from, @Param("to") String to);

    @Update({
            "<script>",
            "UPDATE message SET is_read = 1 WHERE id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</script>"
    })
    int updateIsReadBatch(@Param("ids") List<Long> ids);

    @Select("SELECT COUNT(*) FROM message WHERE receiver_id = #{userId} AND is_read = 0")
    int countUnreadMessages(@Param("userId") String userId);
}
