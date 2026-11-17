package com.mall.user.mapper;

import com.mall.user.dto.response.ConversationResponse;
import com.mall.user.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ConversationMapper {
    int ensureConversation(Conversation conversation);
    Conversation selectById(@Param("conversationId") String conversationId);
    Conversation selectByIdForUpdate(@Param("conversationId") String conversationId);
    Conversation selectByParticipantsForUpdate(@Param("buyerId") String buyerId, @Param("sellerId") String sellerId);
    int updateAfterSend(Conversation conversation);
    int clearUnread(@Param("conversationId") String conversationId, @Param("buyer") boolean buyer);
    long countByParticipant(@Param("userId") String userId);
    List<ConversationResponse> selectPage(@Param("userId") String userId, @Param("offset") long offset, @Param("size") int size);
}
