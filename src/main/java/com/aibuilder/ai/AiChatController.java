package com.aibuilder.ai;

import com.aibuilder.ai.dto.AiChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/conversations/{conversationId}/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(
            @PathVariable Long projectId,
            @PathVariable Long conversationId,
            @RequestBody ChatRequest request
    ) {

        AiChatResponse response =
                aiChatService.chat(
                        projectId,
                        conversationId,
                        request.message()
                );

        return ResponseEntity.ok(response);
    }

    public record ChatRequest(String message) {
    }
}