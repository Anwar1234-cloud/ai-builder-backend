package com.aibuilder.ai;

import com.aibuilder.ai.dto.AiChatResponse;
import com.aibuilder.agent.entity.AgentRun;
import com.aibuilder.agent.service.AgentRunService;
import com.aibuilder.conversation.dto.CreateMessageRequest;
import com.aibuilder.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private final ConversationService conversationService;
    private final AgentRunService agentRunService;
    private final AiAgentAsyncService aiAgentAsyncService;

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

        // Create run immediately
        AgentRun agentRun =
                agentRunService.startRun(
                        projectId,
                        conversationId
                );

        // Start AI in background
        aiAgentAsyncService.execute(
                projectId,
                conversationId,
                agentRun.getId()
        );

        // Return immediately
        return new AiChatResponse(
                agentRun.getId(),
                "AI agent started"
        );
    }
}