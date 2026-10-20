package org.example.ssm1.Service;

import org.example.ssm1.Controller.SseController;
import org.example.ssm1.Entity.Message;
import org.example.ssm1.Mapper.MessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class MessageService {

    @Autowired
    private MessageMapper messageMapper;
    @Autowired
    private SseController sseController;

    public int sendMessage(Message message) {
        int result = messageMapper.insert(message);
        String receiverId = message.getReceiverId();
        sseController.pushMessage(receiverId, "new_message:" + message.getSenderId());
        return result;
    }

    public List<Message> getConversation(String userId1, String userId2) {
        return messageMapper.getConversation(userId1, userId2);
    }
    public List<Message> getMessages(String from, String to) {
        return messageMapper.getMessages(from,to);
    }
    public int markAsRead(Long messageId){
        return messageMapper.updateIsRead(messageId);
    }
    public int markMessagesAsReadBatch(List<Long> messageIds) {
        return messageMapper.updateIsReadBatch(messageIds);
    }

    public int countUnreadMessages(String userId) {
        return messageMapper.countUnreadMessages(userId);
    }

    public List<String> getChatPartners(String userId) {
        return messageMapper.getChatPartners(userId);
    }
}
