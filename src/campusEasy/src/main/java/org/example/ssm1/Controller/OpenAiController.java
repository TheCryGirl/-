package org.example.ssm1.Controller;

import org.example.ssm1.Entity.ChatMessage;
import org.example.ssm1.Service.OpenAiService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@CrossOrigin
@RequestMapping("ai")
public class OpenAiController {
    
    private final OpenAiService openAiService;

    public OpenAiController(OpenAiService openAiService) {
        this.openAiService = openAiService;
    }

    @PostMapping("ingest")
    public String ingest() {
        openAiService.ingestKnowledgeBase();
        return "Ingestion completed";
    }

    @PostMapping("chat")
    public String chat(
            @RequestParam String conversationId,
            @RequestParam String message,
            @RequestParam(required = false) MultipartFile image,
            HttpServletRequest request
    ) {
        String userId = (String) request.getAttribute("currentUser");
        return openAiService.chat(conversationId, message, image, userId);
    }


    @GetMapping("history")
    public List<ChatMessage> getHistory(@RequestParam String conversationId) {
        return openAiService.getHistory(conversationId);
    }
}
