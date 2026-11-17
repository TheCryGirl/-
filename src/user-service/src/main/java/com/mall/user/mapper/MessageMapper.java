package com.mall.user.mapper;

import com.mall.user.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MessageMapper {
    int insert(Message message);
    List<Message> selectPage(@Param("conversationId") String conversationId, @Param("offset") long offset, @Param("size") int size);
    long countByConversation(@Param("conversationId") String conversationId);
    int markReceivedRead(@Param("conversationId") String conversationId, @Param("userId") String userId, @Param("readAt") LocalDateTime readAt);
}
