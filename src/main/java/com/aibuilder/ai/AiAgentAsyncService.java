package com.aibuilder.ai;

import com.aibuilder.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiAgentAsyncService {

    private final AiAgentService aiAgentService;
    private final ConversationService conversationService;

    @Async("aiAgentExecutor")
    public void execute(
            Long projectId,
            Long conversationId,
            Long agentRunId
    ) {

        try {

            AiAgentService.AgentResult result =
                    aiAgentService.run(
                            projectId,
                            conversationId,
                            agentRunId
                    );

            conversationService.addAssistantMessage(
                    projectId,
                    conversationId,
                    result.response()
            );

        } catch (Exception e) {

            conversationService.addAssistantMessage(
                    projectId,
                    conversationId,
                    "I couldn't complete the request. " +
                            "Build/agent failed: " +
                            e.getMessage()
            );
        }
    }
}