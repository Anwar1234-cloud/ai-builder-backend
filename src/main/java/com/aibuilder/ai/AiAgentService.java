package com.aibuilder.ai;

import com.aibuilder.ai.dto.AgentPlan;
import com.aibuilder.ai.tools.ProjectTools;
import com.aibuilder.agent.entity.AgentRun;
import com.aibuilder.agent.entity.AgentTask;
import com.aibuilder.agent.service.AgentRunService;
import com.aibuilder.agent.service.AgentTaskService;
import com.aibuilder.build.entity.BuildRun;
import com.aibuilder.build.service.BuildService;
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
    private final BuildService buildService;
    private static final int MAX_BUILD_ATTEMPTS = 3;

    public AgentResult run(
            Long projectId,
            Long conversationId,
            Long agentRunId
    ) {

        AgentRun agentRun =
                agentRunService.getRun(
                        projectId,
                        agentRunId
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
                                    - Call listFiles once at the start to inspect the project.
                                    - Do NOT call listFiles repeatedly.
                                    - Only call readFile for files that listFiles shows exist.
                                    - NEVER call readFile more than once for the same file path during this task.
                                    - After reading a file, remember its contents and use that information.
                                    - For files that do not exist, call createFile directly.
                                    - Do NOT call readFile to check whether a new file exists.
                                    - Use createFile only for new files.
                                    - Use writeFile only for existing files.
                                    - Use deleteFile only for explicit deletions.
                                    - Do not repeatedly inspect the same file.
                                    - Do not repeatedly perform the same tool call.
                                    - Work only inside the current project.
                                    - Actually modify the project; do not merely describe the solution.
                                    - Stop when the assigned task is complete.
                            
                              UI/UX REQUIREMENT:
                                                                
                                    When the task affects the interface, do not stop after making
                                    the functionality work.
                                                                
                                    Also improve:
                                    - layout
                                    - spacing
                                    - typography
                                    - colors
                                    - responsive behavior
                                    - visual hierarchy
                                    - interactions
                                    - component consistency
                                                                
                                    The final interface should meet the project's UI/UX quality standard.
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
                                            
                                                    - Call listFiles at most once per task.
                                                    - Never read the same file more than once during a task.
                                                    - Do not use readFile to check whether a new file exists.
                                                    - After reading a file, use the returned content and move forward.
                                                    - Do not repeat identical tool calls.
                                                    - Once the requested changes are implemented, stop using tools.

                                                    readFile:
                                                    Read an existing file before modifying it.
                                                                                              
                                                    Rules:
                                                    - Only read files that actually exist.
                                                    - Never read the same file more than once during a task.
                                                    - Do not use readFile to check whether a new file exists.
                                                    - After reading a file, use the returned content and move forward.
                                                    
                                                    TOOL EXECUTION RULES:
                                                            
                                                            - Call listFiles at most once per task.
                                                            - Do not repeat identical tool calls.
                                                            - Do not repeatedly read the same file.
                                                            - Prefer making progress over repeatedly inspecting files.
                                                            - If a file does not exist, create it.
                                                            - If a file exists, read it once and then modify it if needed.
                                                            - Once the requested changes are implemented, stop using tools.

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
                                              
                                              UI/UX QUALITY STANDARD:
                                                      
                                                      Every generated project must have a polished, production-quality UI/UX.
                                                      
                                                      The visual style must adapt to the user's project/domain.
                                                      Do NOT use one fixed visual theme for every project.
                                                      
                                                      For every visual project:
                                                      
                                                      - Create a clear visual hierarchy.
                                                      - Use a strong and purposeful hero/header when appropriate.
                                                      - Use professional typography with clear heading/body contrast.
                                                      - Use a deliberate color palette appropriate to the project.
                                                      - Use consistent spacing, padding, margins, and alignment.
                                                      - Create visually distinct sections rather than one long block.
                                                      - Use polished cards, buttons, inputs, navigation and CTAs.
                                                      - Use meaningful icons where appropriate.
                                                      - Use high-quality imagery or visual backgrounds when appropriate.
                                                      - Use overlays, gradients, borders, shadows and depth thoughtfully.
                                                      - Add hover states and subtle transitions where they improve UX.
                                                      - Make layouts responsive for mobile, tablet and desktop.
                                                      - Ensure text contrast and readability.
                                                      - Maintain consistent border radius, spacing and component styles.
                                                      - Avoid default browser-looking elements.
                                                      - Avoid plain unstyled HTML.
                                                      - Avoid excessive decoration or unnecessary animations.
                                                      - Keep the interface accessible and easy to navigate.
                                                      
                                                      QUALITY BAR:
                                                      
                                                      Before considering the task complete, inspect the generated UI
                                                      and ask yourself:
                                                      
                                                      1. Does the page have a clear visual hierarchy?
                                                      2. Does the first screen immediately communicate the product?
                                                      3. Are the sections visually distinct?
                                                      4. Are typography and spacing consistent?
                                                      5. Are buttons and CTAs visually clear?
                                                      6. Does the design feel intentionally designed rather than generated?
                                                      7. Does it look good on mobile and desktop?
                                                      8. Are there obvious empty, broken or unstyled areas?
                                                      
                                                      If the answer to any important question is no, improve the UI
                                                      before completing the task.
                                                      
                                                      IMPORTANT:
                                                      The goal is not merely working code.
                                                      The goal is a polished, professional application.
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

                        taskResponse =
                                "Task completed via tool calls.";
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


            BuildRun buildRun =
                    buildWithAutoFix(
                            projectId,
                            agentRun.getId(),
                            userRequest,
                            history,
                            tasks
                    );

            if (!"SUCCESS".equals(
                    buildRun.getStatus().name()
            )) {

                throw new RuntimeException(
                        "Project build failed after "
                                + MAX_BUILD_ATTEMPTS
                                + " attempts"
                                + "\n\nBuild output:\n"
                                + buildRun.getOutput()
                                + "\n\nBuild errors:\n"
                                + buildRun.getErrorOutput()
                );
            }

            agentRunService.completeRun(
                    agentRun.getId()
            );

            projectVersionService.createSnapshot(
                    projectId,
                    "AI agent changes - build successful",
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

    private BuildRun buildWithAutoFix(
            Long projectId,
            Long agentRunId,
            String userRequest,
            List<MessageResponse> history,
            List<AgentTask> tasks
    ) {

        BuildRun latestBuild = null;

        for (int attempt = 1;
             attempt <= MAX_BUILD_ATTEMPTS;
             attempt++) {


            latestBuild =
                    buildService.createBuild(
                            projectId,
                            agentRunId,
                            "npm run build"
                    );

            latestBuild =
                    buildService.executeBuild(
                            latestBuild.getId()
                    );


            if (latestBuild.getStatus().name()
                    .equals("SUCCESS")) {

                return latestBuild;
            }


            if (attempt == MAX_BUILD_ATTEMPTS) {
                return latestBuild;
            }


            int nextTaskOrder =
                    tasks.size() + 1;

            AgentTask fixTask =
                    agentTaskService.createTask(
                            agentRunId,
                            "Fix build errors - attempt " + attempt,
                            """
                            Fix the build errors reported by the project build.
    
                            Build command:
                            npm run build
    
                            Build output:
                            %s
    
                            Build errors:
                            %s
                            """.formatted(
                                    latestBuild.getOutput(),
                                    latestBuild.getErrorOutput()
                            ),
                            nextTaskOrder
                    );

            tasks.add(fixTask);

            agentTaskService.startTask(
                    fixTask.getId()
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
                            // Ignore system messages.
                        }
                    }
                }


                String fixInstruction =
                        """
                        The project build failed.
    
                        Your job is to diagnose and fix the build.
    
                        Original user request:
                        %s
    
                        Build attempt:
                        %d
    
                        Build output:
                        %s
    
                        Build error:
                        %s
    
                        IMPORTANT:
                        - Inspect the relevant files first.
                        - Use readFile before modifying existing files.
                        - Actually fix the source/project files.
                        - Do not merely explain the error.
                        - Do not invent files.
                        - Do not simulate deletion.
                        - After fixing the problem, stop.
                        """.formatted(
                                userRequest,
                                attempt,
                                latestBuild.getOutput(),
                                latestBuild.getErrorOutput()
                        );

                messages.add(
                        new UserMessage(
                                fixInstruction
                        )
                );

                ChatClient chatClient =
                        chatClientBuilder.build();

                String fixResponse =
                        chatClient
                                .prompt()

                                .system(
                                        """
                                        You are the build-fix agent
                                        for an AI application builder.
    
                                        Diagnose build/compiler errors
                                        and modify the project to fix them.
    
                                        Available tools:
    
                                        listFiles:
                                        Inspect project structure.
    
                                        readFile:
                                        Read existing files.
    
                                        createFile:
                                        Create missing files.
    
                                        writeFile:
                                        Update existing files.
    
                                        deleteFile:
                                        Delete files only when explicitly required.
    
                                        Always inspect the relevant files
                                        before changing them.
    
                                        Your goal is to leave the project
                                        in a buildable state.
                                        """
                                )

                                .messages(messages)

                                .tools(projectTools)

                                .toolContext(
                                        Map.of(
                                                "projectId",
                                                projectId,

                                                "agentRunId",
                                                agentRunId,

                                                "agentTaskId",
                                                fixTask.getId()
                                        )
                                )

                                .call()

                                .content();

                if (fixResponse == null ||
                        fixResponse.isBlank()) {

                    fixResponse =
                            "Fix completed via tool calls";


                }

                agentTaskService.completeTask(
                        fixTask.getId()
                );

            } catch (Exception e) {

                agentTaskService.failTask(
                        fixTask.getId(),
                        e.getMessage()
                );

                throw e;
            }
        }

        return latestBuild;
    }

    public record AgentResult(
            Long agentRunId,
            String response
    ) {
    }
}