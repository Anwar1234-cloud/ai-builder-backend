package com.aibuilder.ai;

import com.aibuilder.ai.dto.AgentPlan;
import com.aibuilder.ai.tools.ProjectTools;
import com.aibuilder.agent.entity.AgentRun;
import com.aibuilder.agent.entity.AgentTask;
import com.aibuilder.agent.service.AgentRunService;
import com.aibuilder.agent.service.AgentTaskService;
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
    private final AgentTaskService agentTaskService;
    private final ProjectVersionService projectVersionService;
    private final AiPlannerService aiPlannerService;

    public AgentResult run(
            Long projectId,
            Long conversationId
    ) {

        AgentRun agentRun =
                agentRunService.startRun(
                        projectId,
                        conversationId
                );

        List<AgentTask> tasks =
                new ArrayList<>();

        try {

            List<MessageResponse> history =
                    conversationService.getMessages(
                            projectId,
                            conversationId
                    );

            String userRequest =
                    history.stream()
                            .filter(message ->
                                    message.getRole()
                                            .name()
                                            .equals("USER"))
                            .reduce(
                                    (first, second) -> second
                            )
                            .map(
                                    MessageResponse::getContent
                            )
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "No user message found"
                                    )
                            );


            AgentPlan plan =
                    aiPlannerService.createPlan(
                            projectId,
                            userRequest
                    );

            if (plan.tasks() == null ||
                    plan.tasks().isEmpty()) {

                throw new RuntimeException(
                        "AI planner returned no tasks"
                );
            }


            tasks =
                    agentTaskService.createTasksFromPlan(
                            agentRun.getId(),
                            plan.tasks()
                    );

            String finalResponse = null;


            for (AgentTask task : tasks) {

                agentTaskService.startTask(
                        task.getId()
                );

                try {

                    List<Message> messages =
                            new ArrayList<>();


                    for (MessageResponse message :
                            history) {

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

                            }
                        }
                    }


                    String taskInstruction =
                            """
                            Execute the following task from the AI plan.

                            Task title:
                            %s

                            Task description:
                            %s

                            Original user request:
                            %s

                            IMPORTANT:
                            - Actually perform the task.
                            - Use the project tools.
                            - Read existing files before changing them.
                            - Use createFile only for new files.
                            - Use writeFile only for existing files.
                            - Use deleteFile for explicit deletions.
                            - Work only inside the current project.
                            - Do not merely describe the solution.
                            - Stop after this task is complete.
                            """.formatted(
                                    task.getTitle(),
                                    task.getDescription(),
                                    userRequest
                            );

                    messages.add(
                            new UserMessage(
                                    taskInstruction
                            )
                    );

                    ChatClient chatClient =
                            chatClientBuilder.build();

                    String taskResponse =
                            chatClient
                                    .prompt()

                                    .system(
                                            """
                                            You are the execution agent
                                            of an AI application builder.

                                            You build:
                                            - Websites
                                            - Web applications
                                            - Mobile applications
                                            - Full-stack applications

                                            Available tools:

                                            listFiles:
                                            Inspect project structure.

                                            readFile:
                                            Read an existing file before
                                            modifying it.

                                            createFile:
                                            Create a file that does not exist.

                                            writeFile:
                                            Update an existing file only.

                                            deleteFile:
                                            Delete an existing file when
                                            explicitly requested.

                                            IMPORTANT:
                                            - Perform actual project changes.
                                            - Never invent file contents.
                                            - Work only with the current project.
                                            - Complete the assigned task.
                                            - Never simulate deletion by
                                              writing placeholder text.
                                            """
                                    )

                                    .messages(messages)

                                    .tools(projectTools)

                                    .toolContext(
                                            Map.of(
                                                    "projectId",
                                                    projectId,

                                                    "agentRunId",
                                                    agentRun.getId(),

                                                    "agentTaskId",
                                                    task.getId()
                                            )
                                    )

                                    .call()

                                    .content();

                    if (taskResponse == null ||
                            taskResponse.isBlank()) {

                        throw new RuntimeException(
                                "AI returned an empty response for task: "
                                        + task.getTitle()
                        );
                    }


                    agentTaskService.completeTask(
                            task.getId()
                    );

                    finalResponse =
                            taskResponse;

                } catch (Exception taskException) {

                    agentTaskService.failTask(
                            task.getId(),
                            taskException.getMessage()
                    );

                    throw taskException;
                }
            }


            agentRunService.completeRun(
                    agentRun.getId()
            );


            projectVersionService.createSnapshot(
                    projectId,
                    "AI agent changes",
                    "AI_AGENT"
            );

            return new AgentResult(
                    agentRun.getId(),
                    finalResponse
            );

        } catch (Exception e) {

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