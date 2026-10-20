package org.example.ssm1.Controller;

import org.example.ssm1.Entity.Message;
import org.example.ssm1.Service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@CrossOrigin
public class MessageController {

    @Autowired
    private MessageService messageService;

    @PostMapping
    public ResponseEntity<Integer> sendMessage(@RequestBody Message message) {
        return ResponseEntity.ok(messageService.sendMessage(message));
    }
    @Transactional
    @PostMapping("/read")
    public ResponseEntity<Void> markMessageAsRead(@RequestBody Message message) {
        Long messageId = message.getId();
        messageService.markAsRead(messageId);
        return ResponseEntity.ok().build();
    }
    @GetMapping("/unread")
    public ResponseEntity<Boolean> hasUnreadMessages(@RequestParam String from, @RequestParam String currentId) {
        List<Message> conversation = messageService.getMessages(from, currentId);
        for (Message message : conversation) {
            if(message.getIsRead()==false) return ResponseEntity.ok(true);
        }
        return ResponseEntity.ok(false);
    }
    @GetMapping("unread/count/each")
    public ResponseEntity<Integer> eachHasUnreadMessages(@RequestParam String from, @RequestParam String currentId) {
        List<Message> conversation = messageService.getMessages(from, currentId);
        int count = 0;
        for (Message message : conversation) {
            if(message.getIsRead()==false) count++;
        }
        return ResponseEntity.ok(count);
    }

    @GetMapping("/unread/count")
    public ResponseEntity<Integer> getUnreadMessageCount(@RequestParam String userId) {
        int count = messageService.countUnreadMessages(userId);
        return ResponseEntity.ok(count);
    }
    @PostMapping("/read-batch")
    public ResponseEntity<Void> markMessagesAsReadBatch(@RequestBody Map<String, Object> payload) {
        List<Long> ids = (List<Long>) payload.get("ids");
        String userId = (String) payload.get("userId");
        messageService.markMessagesAsReadBatch(ids);
        return ResponseEntity.ok().build();
    }
    @GetMapping("/conversation")
    public ResponseEntity<List<Message>> getConversation(@RequestParam String userId1, @RequestParam String userId2) {
        return ResponseEntity.ok(messageService.getConversation(userId1, userId2));
    }

    @GetMapping("/partners/{userId}")
    public ResponseEntity<List<String>> getChatPartners(@PathVariable String userId) {
        return ResponseEntity.ok(messageService.getChatPartners(userId));
    }
}
