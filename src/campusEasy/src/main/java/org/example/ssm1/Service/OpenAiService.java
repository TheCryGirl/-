package org.example.ssm1.Service;

import org.example.ssm1.Entity.ChatMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class OpenAiService {

    private final ChatClient chatClient;
    private final ChatClient multiModalChatClient;
    private final ChatMemoryService chatMemoryService;
    private final RagService ragService;

    public OpenAiService(ChatClient.Builder builder,
                         ToolService toolService,
                         ChatMemoryService chatMemoryService,
                         RagService ragService) {
        this.multiModalChatClient = builder.build();
        this.chatClient = builder
                .defaultTools(toolService)
                .defaultAdvisors(new SafeGuardAdvisor(List.of("敏感词")))
                .build();

        this.chatMemoryService = chatMemoryService;
        this.ragService = ragService;
    }

    public void ingestKnowledgeBase() {
        ragService.ingestKnowledgeBase();
    }

    public String chat(String conversationId, String message, MultipartFile image, String userId) {
        String systemContext = "";
        if (userId != null) {
            systemContext = "System Info: Current User ID is " + userId + ". You can use this ID for tools that require it.\n";
        }

        String fullPrompt = systemContext + chatMemoryService.buildPrompt(conversationId, message);

        chatMemoryService.saveMessage(conversationId, "USER", message);

        ChatClient clientToUse = (image != null && !image.isEmpty()) ? multiModalChatClient : chatClient;

        if (image != null && !image.isEmpty()) {
            String contentType = image.getContentType();
            if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png"))) {
                return "仅支持 JPG 或 PNG 格式的图片";
            }
        }

        var promptRequest = clientToUse.prompt().user(u -> {
            u.text(fullPrompt);
            if (image != null && !image.isEmpty()) {
                try {
                    byte[] imageBytes = image.getBytes();
                    u.media(MimeTypeUtils.parseMimeType(image.getContentType()), new ByteArrayResource(imageBytes));
                } catch (IOException e) {
                    System.err.println("Error processing image: " + e.getMessage());
                    throw new RuntimeException("Failed to process image", e);
                }
            }
        });

        String reply = promptRequest.call().content();
        chatMemoryService.saveMessage(conversationId, "ASSISTANT", reply);
        return reply;
    }

    public List<ChatMessage> getHistory(String conversationId) {
        return chatMemoryService.getMessages(conversationId);
    }
}
