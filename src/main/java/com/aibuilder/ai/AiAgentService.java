package com.aibuilder.ai;

import com.aibuilder.ai.tools.ProjectTools;
import com.aibuilder.conversation.dto.MessageResponse;
import com.aibuilder.conversation.service.ConversationService;
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

    public String run(
            Long projectId,
            Long conversationId
    ) {

        // Load complete conversation history
        List<MessageResponse> history =
                conversationService.getMessages(
                        projectId,
                        conversationId
                );

        // Convert database messages into Spring AI messages
        List<Message> messages = new ArrayList<>();

        for (MessageResponse message : history) {

            switch (message.getRole()) {

                case USER -> messages.add(
                        new UserMessage(message.getContent())
                );

                case ASSISTANT -> messages.add(
                        new AssistantMessage(message.getContent())
                );

                case SYSTEM -> {
                    // System instructions are supplied separately.
                }
            }
        }

        ChatClient chatClient =
                chatClientBuilder.build();

        String response = chatClient
                .prompt()
                .system("""
                        You are the AI agent for an AI application builder.

                        You help users build:
                        - Websites
                        - Web applications
                        - Mobile applications
                        - Full-stack applications

                        You have access to tools that operate on the user's
                        current project.

                        TOOL RULES:

                        1. listFiles
                           Use this to inspect the project's existing files.

                        2. readFile
                           Use this before modifying an existing file.

                        3. createFile
                           Use this only when a new file is needed.

                        4. writeFile
                           Use this only to update an existing file.
                           Always write the complete file content.

                        5. deleteFile
                           Use this when the user explicitly asks to delete
                           or remove a file.

                        Never simulate deletion by writing blank,
                        empty, or placeholder content.

                        Never assume files exist.
                        Inspect the project when necessary.

                        Complete the requested operation using the tools,
                        rather than merely returning code in the chat.

                        After completing the operation, give the user
                        a short summary of what was done.
                        """)
                .messages(messages)
                .tools(projectTools)
                .toolContext(
                        Map.of(
                                "projectId", projectId
                        )
                )
                .call()
                .content();

        if (response == null || response.isBlank()) {
            throw new RuntimeException(
                    "AI agent returned an empty response"
            );
        }

        return response;
    }
}