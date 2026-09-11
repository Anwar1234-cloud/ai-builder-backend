package com.aibuilder.ai;

import com.aibuilder.ai.tools.ProjectTools;
import com.aibuilder.agent.entity.AgentRun;
import com.aibuilder.agent.service.AgentRunService;
import com.aibuilder.conversation.dto.MessageResponse;
import com.aibuilder.conversation.service.ConversationService;
import com.aibuilder.version.service.ProjectVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiAgentService {

    private final ChatClient.Builder chatClientBuilder;
    private final ConversationService conversationService;
    private final ProjectTools projectTools;
    private final AgentRunService agentRunService;
    private final ProjectVersionService projectVersionService;

    public AgentResult run(Long projectId, Long conversationId) {

        AgentRun agentRun =
                agentRunService.startRun(projectId, conversationId);

        try {

            List<MessageResponse> history =
                    conversationService.getMessages(
                            projectId,
                            conversationId
                    );

            List<Message> messages = new ArrayList<>();

            for (MessageResponse message : history) {

                switch (message.getRole()) {

                    case USER ->
                            messages.add(
                                    new UserMessage(
                                            message.getContent()
                                    )
                            );

                    case ASSISTANT ->
                            messages.add(
                                    new AssistantMessage(
                                            message.getContent()
                                    )
                            );

                    case SYSTEM -> {
                        // Ignore SYSTEM messages for now.
                    }
                }
            }

            ChatClient chatClient =
                    chatClientBuilder.build();

            String response =
                    chatClient
                            .prompt()

                            .system("""
                                    You are the AI agent for an AI application builder.

                                    You help users build:
                                    - Websites
                                    - Web applications
                                    - Mobile applications
                                    - Full-stack applications

                                    You can inspect and modify the user's project
                                    using the available tools.

                                    TOOL RULES:

                                    listFiles:
                                    Use it to inspect the project structure.

                                    readFile:
                                    Before modifying an existing file, read it first.

                                    createFile:
                                    Use it only when the requested file does not exist.

                                    writeFile:
                                    Use it only to update an existing file.
                                    Never use it to simulate deletion.

                                    deleteFile:
                                    Use it whenever the user explicitly asks
                                    to delete or remove a file.

                                    IMPORTANT:
                                    - Work only with the current project.
                                    - Do not invent files.
                                    - Do not assume file contents.
                                    - Use tools to perform requested changes.
                                    - Do not merely display code when the user
                                      asks you to modify the project.
                                    - Once the requested work is complete,
                                      stop using tools and provide a concise summary.
                                    """)

                            .messages(messages)

                            .tools(projectTools)

                            .toolContext(
                                    Map.of(
                                            "projectId", projectId,
                                            "agentRunId", agentRun.getId()
                                    )
                            )

                            .call()

                            .content();

            if (response == null || response.isBlank()) {
                throw new RuntimeException(
                        "AI agent returned an empty response"
                );
            }

            /*
             * AI work completed successfully.
             *
             * First mark the AgentRun as completed.
             */
            agentRunService.completeRun(agentRun.getId());

            /*
             * Now create a complete snapshot of the project.
             *
             * This stores the current state of every ProjectFile.
             */
            projectVersionService.createSnapshot(
                    projectId,
                    "AI agent changes",
                    "AI_AGENT"
            );

            return new AgentResult(
                    agentRun.getId(),
                    response
            );

        } catch (Exception e) {

            /*
             * If anything fails, the run is marked FAILED
             * and no snapshot is created.
             */
            agentRunService.failRun(
                    agentRun.getId(),
                    e.getMessage()
            );

            throw e;
        }
    }

    public record AgentResult(
            Long agentRunId,
            String response
    ) {
    }
}