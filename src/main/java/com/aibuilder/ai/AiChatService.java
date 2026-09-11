package com.aibuilder.ai;

import com.aibuilder.ai.dto.AiChatResponse;
import com.aibuilder.conversation.dto.CreateMessageRequest;
import com.aibuilder.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private final ConversationService conversationService;
    private final AiAgentService aiAgentService;

    public AiChatResponse chat(
            Long projectId,
            Long conversationId,
            String userMessage
    ) {

        // Save user message
        CreateMessageRequest request =
                new CreateMessageRequest();

        request.setContent(userMessage);

        conversationService.addUserMessage(
                projectId,
                conversationId,
                request
        );

        // Run AI agent
        AiAgentService.AgentResult result =
                aiAgentService.run(
                        projectId,
                        conversationId
                );

        // Save assistant response
        conversationService.addAssistantMessage(
                projectId,
                conversationId,
                result.response()
        );

        return new AiChatResponse(
                result.agentRunId(),
                result.response()
        );
    }
}