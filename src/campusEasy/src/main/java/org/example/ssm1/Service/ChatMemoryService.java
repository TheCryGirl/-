package org.example.ssm1.Service;

import org.example.ssm1.Entity.ChatMessage;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatMemoryService {

    private final JdbcTemplate jdbcTemplate;
    private final RagService ragService;

    public ChatMemoryService(JdbcTemplate jdbcTemplate, RagService ragService) {
        this.jdbcTemplate = jdbcTemplate;
        this.ragService = ragService;
    }

    public void saveMessage(String conversationId, String role, String content) {
        jdbcTemplate.update(
                "INSERT INTO chat_memory (conversation_id, role, content) VALUES (?, ?, ?)",
                conversationId, role, content
        );
    }

    public List<ChatMessage> getMessages(String conversationId) {
        return jdbcTemplate.query(
                "SELECT role, content FROM chat_memory WHERE conversation_id = ? ORDER BY id ASC",
                (rs, rowNum) -> new ChatMessage(rs.getString("role"), rs.getString("content")),
                conversationId
        );
    }

    public String buildPrompt(String conversationId, String newMessage) {
        List<Document> similarDocuments = ragService.retrieve(newMessage);
        String context = similarDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        List<ChatMessage> history = getMessages(conversationId);
        if (history.size() > 10) {
            history = history.subList(history.size() - 10, history.size());
        }

        StringBuilder prompt = new StringBuilder();
        if (!context.isEmpty()) {
            prompt.append("Relevant Context:\n")
                  .append(context)
                  .append("\n\n");
        }

        prompt.append("Conversation History:\n");
        for (ChatMessage msg : history) {
            prompt.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }
        prompt.append("USER: ").append(newMessage).append("\n");
        
        return prompt.toString();
    }
}