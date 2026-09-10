package com.aibuilder.ai;

import com.aibuilder.conversation.dto.CreateMessageRequest;
import com.aibuilder.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private final ConversationService conversationService;
    private final AiAgentService aiAgentService;

    public String chat(
            Long projectId,
            Long conversationId,
            String userMessage
    ) {

        // 1. Save user's message
        CreateMessageRequest request =
                new CreateMessageRequest();

        request.setContent(userMessage);

        conversationService.addUserMessage(
                projectId,
                conversationId,
                request
        );

        // 2. Run AI agent
        String response =
                aiAgentService.run(
                        projectId,
                        conversationId
                );

        // 3. Save assistant response
        conversationService.addAssistantMessage(
                projectId,
                conversationId,
                response
        );

        return response;
    }
}