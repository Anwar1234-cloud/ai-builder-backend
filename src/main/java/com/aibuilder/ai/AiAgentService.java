package com.aibuilder.ai;

import com.aibuilder.agent.entity.AgentTaskType;
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
import com.aibuilder.preview.service.ScreenshotService;
import com.aibuilder.visual.VisualReviewResult;
import com.aibuilder.visual.VisualReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import com.aibuilder.workspace.service.WorkspaceFileCacheService;
import com.aibuilder.agent.service.AgentToolCallService;
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
    private final AgentToolCallService agentToolCallService;
    private final WorkspaceFileCacheService workspaceFileCacheService;
    private final AgentTaskService agentTaskService;
    private final ProjectVersionService projectVersionService;
    private final AiPlannerService aiPlannerService;
    private final AiGenerationRetryService aiGenerationRetryService;
    private final BuildService buildService;

    private final ScreenshotService screenshotService;
    private final VisualReviewService visualReviewService;

    private static final int MAX_BUILD_ATTEMPTS = 3;
    private static final int MAX_TASK_EXECUTION_ATTEMPTS = 2;
    private static final int MAX_VISUAL_FIX_ATTEMPTS = 2;

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

                    String taskResponse =
                            executeTaskWithRetry(
                                    projectId,
                                    agentRun.getId(),
                                    task,
                                    userRequest,
                                    history
                            );

                    agentTaskService.completeTask(
                            task.getId()
                    );

                    finalResponse = taskResponse;

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


         
            buildRun =
                    validateVisualsWithAutoFix(
                            projectId,
                            agentRun.getId(),
                            userRequest,
                            history,
                            tasks,
                            buildRun
                    );


            if (!"SUCCESS".equals(
                    buildRun.getStatus().name()
            )) {

                throw new RuntimeException(
                        "Project failed during visual repair rebuild."
                );
            }


            agentRunService.completeRun(
                    agentRun.getId()
            );


          
            projectVersionService.createSnapshot(
                    projectId,
                    "AI agent changes - build and visual validation successful",
                    "AI_AGENT"
            );

            workspaceFileCacheService.clear(
                    agentRun.getId()
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

            workspaceFileCacheService.clear(
                    agentRun.getId()
            );

            throw e;
        }
    }

    private BuildRun validateVisualsWithAutoFix(
            Long projectId,
            Long agentRunId,
            String userRequest,
            List<MessageResponse> history,
            List<AgentTask> tasks,
            BuildRun initialBuild
    ) {

        BuildRun currentBuild =
                initialBuild;


        for (int visualAttempt = 0;
             visualAttempt <= MAX_VISUAL_FIX_ATTEMPTS;
             visualAttempt++) {


            System.out.println(
                    "[VISUAL] Reviewing build "
                            + currentBuild.getId()
                            + " | repairAttempt="
                            + visualAttempt
                            + "/"
                            + MAX_VISUAL_FIX_ATTEMPTS
            );



            ScreenshotService.ScreenshotResult screenshots =
                    screenshotService.captureScreenshots(
                            projectId,
                            currentBuild.getId()
                    );



            VisualReviewResult review =
                    visualReviewService.review(
                            screenshots.viewportScreenshot(),
                            screenshots.fullPageScreenshot()
                    );


            System.out.println(
                    "[VISUAL] Build "
                            + currentBuild.getId()
                            + " verdict="
                            + review.verdict()
            );



            if (!review.needsFix()) {

                System.out.println(
                        "[VISUAL] Visual validation PASSED "
                                + "for build "
                                + currentBuild.getId()
                );

                return currentBuild;
            }



            if (visualAttempt ==
                    MAX_VISUAL_FIX_ATTEMPTS) {

                throw new RuntimeException(
                        "Visual validation failed after "
                                + MAX_VISUAL_FIX_ATTEMPTS
                                + " repair attempts."
                                + "\n\nSummary:\n"
                                + review.summary()
                                + "\n\nIssues:\n"
                                + formatVisualIssues(
                                review.issues()
                        )
                                + "\n\nRecommended fix:\n"
                                + review.fixInstructions()
                );
            }



            int repairNumber =
                    visualAttempt + 1;

            int nextTaskOrder =
                    tasks.stream()
                            .map(AgentTask::getTaskOrder)
                            .filter(java.util.Objects::nonNull)
                            .max(Integer::compareTo)
                            .orElse(0)
                            + 1;


            AgentTask visualFixTask =
                    agentTaskService.createTask(
                            agentRunId,

                            "Fix visual defects - attempt "
                                    + repairNumber,

                            """
                            The generated application compiled successfully,
                            but visual inspection of the real Chromium render
                            found significant UI/rendering defects.
    
                            Original user request:
                            %s
    
                            Visual review summary:
                            %s
    
                            Visible issues:
                            %s
    
                            Required repair:
                            %s
    
                            VISUAL REPAIR REQUIREMENTS:
    
                            1. Inspect the existing project before editing.
    
                            2. Determine the ROOT CAUSE of the visible defects.
                               Do not blindly redesign the application.
    
                            3. Pay special attention to:
                               - stylesheet imports
                               - Tailwind/CSS framework configuration
                               - package dependencies
                               - Vite/PostCSS configuration
                               - responsive layout classes
                               - duplicate desktop/mobile navigation
                               - container width constraints
                               - overflow
                               - image sizing
                               - component visibility rules
                               - broken or missing design-system styles
    
                            4. If styling exists but is not being applied,
                               repair the styling integration rather than
                               rewriting the whole application.
    
                            5. Preserve the intended visual direction and
                               unrelated functionality.
    
                            6. You MUST modify project files using
                               createFile, writeFile or deleteFile.
    
                            7. Do not merely describe the visual problem.
    
                            8. Do not stop after inspecting files.
    
                            9. Make the minimum coherent changes required
                               to resolve the reported defects.
    
                            10. Stop after implementing the repair.
    
                            The repair is successful only when actual
                            project files have been modified.
                            """.formatted(
                                    userRequest,
                                    safeVisualText(
                                            review.summary()
                                    ),
                                    formatVisualIssues(
                                            review.issues()
                                    ),
                                    safeVisualText(
                                            review.fixInstructions()
                                    )
                            ),

                            nextTaskOrder,

                            AgentTaskType.VISUAL_FIX
                    );


            tasks.add(
                    visualFixTask
            );



            agentTaskService.startTask(
                    visualFixTask.getId()
            );


            try {

                executeVisualFixTask(
                        projectId,
                        agentRunId,
                        visualFixTask,
                        userRequest,
                        history,
                        review,
                        repairNumber
                );


                agentTaskService.completeTask(
                        visualFixTask.getId()
                );


            } catch (Exception e) {

                agentTaskService.failTask(
                        visualFixTask.getId(),
                        e.getMessage()
                );

                throw e;
            }



            currentBuild =
                    buildWithAutoFix(
                            projectId,
                            agentRunId,
                            userRequest,
                            history,
                            tasks
                    );


            if (!"SUCCESS".equals(
                    currentBuild.getStatus().name()
            )) {

                throw new RuntimeException(
                        "Visual repair attempt "
                                + repairNumber
                                + " caused a build failure "
                                + "that could not be repaired."
                );
            }



        }


        throw new RuntimeException(
                "Unexpected visual validation state"
        );
    }

    private void executeVisualFixTask(
            Long projectId,
            Long agentRunId,
            AgentTask visualFixTask,
            String userRequest,
            List<MessageResponse> history,
            VisualReviewResult review,
            int repairNumber
    ) {

        List<Message> messages =
                new ArrayList<>();


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

                }
            }
        }


        String instruction =
                """
                Perform VISUAL_FIX attempt %d.
    
                The project already compiled successfully,
                but browser screenshot inspection found
                significant visual defects.
    
                Original user request:
                %s
    
                Visual review summary:
                %s
    
                Issues:
                %s
    
                Fix instructions:
                %s
    
                WORKFLOW:
    
                1. Inspect the minimum necessary project files.
    
                2. Prefer one listFiles call at most.
    
                3. Batch related file inspection using readFiles.
    
                4. Determine whether the problem comes from:
                   - CSS not being imported
                   - CSS framework configuration
                   - Tailwind/PostCSS/version mismatch
                   - missing styles
                   - incorrect layout classes
                   - duplicate components
                   - desktop/mobile visibility rules
                   - width/max-width constraints
                   - overflow
                   - malformed responsive layout
                   - image sizing
                   - component structure
    
                5. Fix the ROOT CAUSE.
    
                6. You MUST call createFile, writeFile or
                   deleteFile successfully.
    
                7. Do NOT only inspect the project.
    
                8. Do NOT replace the entire application
                   unless absolutely necessary.
    
                9. Preserve unrelated functionality.
    
                10. Stop after the visual defect has been
                    implemented correctly.
    
                A textual explanation is NOT a successful
                visual repair.
    
                The project files MUST actually change.
                """.formatted(
                        repairNumber,
                        userRequest,
                        safeVisualText(
                                review.summary()
                        ),
                        formatVisualIssues(
                                review.issues()
                        ),
                        safeVisualText(
                                review.fixInstructions()
                        )
                );


        messages.add(
                new UserMessage(
                        instruction
                )
        );


        ChatClient chatClient =
                chatClientBuilder.build();


        String response =
                aiGenerationRetryService.execute(
                        "Visual fix attempt "
                                + repairNumber,

                        () -> chatClient
                                .prompt()

                                .system(
                                        """
                                        You are the visual-repair agent
                                        of a professional AI application builder.
    
                                        Another AI reviewer has inspected a real
                                        Chromium screenshot of the generated
                                        application and identified significant
                                        rendering or styling defects.
    
                                        Your responsibility is to FIX the actual
                                        project files.
    
                                        You are not the visual reviewer.
                                        Do not debate the review.
    
                                        Diagnose the implementation root cause
                                        behind the reported visible defects.
    
                                        AVAILABLE TOOLS:
    
                                        listFiles
                                        readFiles
                                        readFile
                                        createFile
                                        writeFile
                                        deleteFile
    
                                        REQUIRED WORKFLOW:
    
                                        visual defect
                                            ->
                                        inspect minimum relevant files
                                            ->
                                        identify implementation root cause
                                            ->
                                        modify project
                                            ->
                                        stop
    
                                        IMPORTANT:
    
                                        Inspection alone is never sufficient
                                        for VISUAL_FIX.
    
                                        You MUST modify the project.
    
                                        Prefer readFiles when several related
                                        files need inspection.
    
                                        Do not repeatedly read the same files.
    
                                        Pay particular attention to stylesheet
                                        integration and CSS framework setup when
                                        the screenshot appears largely unstyled.
    
                                        If Tailwind or another CSS framework is
                                        configured incorrectly, repair the
                                        configuration/version integration instead
                                        of manually recreating every utility style.
    
                                        If desktop and mobile components are both
                                        visible simultaneously, repair responsive
                                        visibility/layout rules.
    
                                        Preserve the intended design and unrelated
                                        functionality.
    
                                        Do not merely explain what should change.
    
                                        Your work is complete only after project
                                        files have actually been modified.
                                        """
                                )

                                .messages(
                                        messages
                                )

                                .tools(
                                        projectTools
                                )

                                .toolContext(
                                        Map.of(
                                                "projectId",
                                                projectId,

                                                "agentRunId",
                                                agentRunId,

                                                "agentTaskId",
                                                visualFixTask.getId()
                                        )
                                )

                                .call()

                                .content()
                );


        boolean madeChanges =
                agentToolCallService
                        .hasSuccessfulModification(
                                visualFixTask.getId()
                        );


        boolean usedTools =
                agentToolCallService
                        .hasAnySuccessfulToolCall(
                                visualFixTask.getId()
                        );


        System.out.println(
                "[VISUAL-FIX] Task "
                        + visualFixTask.getId()
                        + " | usedTool="
                        + usedTools
                        + " | modifiedProject="
                        + madeChanges
        );


        if (!madeChanges) {

            throw new RuntimeException(
                    "VISUAL_FIX task did not modify "
                            + "any project files."
            );
        }


        if (response == null ||
                response.isBlank()) {

            System.out.println(
                    "[VISUAL-FIX] Gemini returned no "
                            + "text response, but project "
                            + "modifications were recorded."
            );
        }
    }

    private String executeTaskWithRetry(
            Long projectId,
            Long agentRunId,
            AgentTask task,
            String userRequest,
            List<MessageResponse> history
    ) {

        RuntimeException lastException = null;
        String previousRejectionReason = null;

        for (int executionAttempt = 1;
             executionAttempt <= MAX_TASK_EXECUTION_ATTEMPTS;
             executionAttempt++) {

            try {

                System.out.println(
                        "[AGENT] Executing task "
                                + task.getId()
                                + " - "
                                + task.getTitle()
                                + " | type="
                                + task.getTaskType()
                                + " | executionAttempt="
                                + executionAttempt
                                + "/"
                                + MAX_TASK_EXECUTION_ATTEMPTS
                );

                List<Message> messages =
                        new ArrayList<>();

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

                        }
                    }
                }


                String retryInstruction;

                if (executionAttempt == 1) {

                    retryInstruction = "";

                } else if (task.getTaskType() == AgentTaskType.IMPLEMENTATION) {

                    retryInstruction =
                            """
                            
                            ==================================================
                            IMPLEMENTATION RECOVERY MODE
                            ==================================================
                            
                                    Your previous execution attempt was rejected.
                                                                
                                    Reason:
                                    %s
                                                                
                                    You inspected the project but did not complete the required
                                    project modification.
                            
                            This is now a recovery attempt.
                            
                            IMPORTANT:
                            
                            - Do NOT restart the task from analysis.
                            - Do NOT spend this attempt only inspecting files.
                            - Do NOT call listFiles more than once.
                            - After inspecting the minimum required existing files,
                              you MUST proceed to implementation.
                            - You MUST call createFile, writeFile or deleteFile
                              successfully during this attempt.
                            - Prefer modifying the existing relevant application
                              files instead of creating unnecessary duplicates.
                            - If the required UI belongs in an existing component,
                              update that component with writeFile.
                            - If a required component genuinely does not exist,
                              create it with createFile.
                            - Do not stop after listFiles/readFiles.
                            - Do not return a textual explanation instead of
                              modifying the project.
                            
                            THIS ATTEMPT IS SUCCESSFUL ONLY IF PROJECT FILES
                            ARE ACTUALLY MODIFIED.
                            
                            Inspect only what is absolutely necessary, then
                            IMPLEMENT THE TASK.
                            """.formatted(
                                   previousRejectionReason == null
                                           ? "Implementation requirements were not satisfied."
                                           : previousRejectionReason
                            );

                } else {

                    retryInstruction =
                            """
                            
                            PREVIOUS EXECUTION ATTEMPT WAS REJECTED.
                            
                            The previous attempt did not satisfy the completion
                            requirements.
                            
                            Correct the problem and complete the assigned task.
                            
                            Use the project tools and do not repeat unnecessary
                            inspection.
                            """;
                }


                String taskInstruction =
                        """
                        Execute the following task from the AI plan.
    
                        Task title:
                        %s
    
                        Task type:
                        %s
    
                        Task description:
                        %s
    
                        Original user request:
                        %s
    
                        TASK TYPE REQUIREMENTS:
    
                        ANALYSIS:
                        Inspect the project using the available tools.
                        Modification is optional.
    
                                IMPLEMENTATION:
                                You MUST implement the requested functionality in the project.
                                                        
                                Inspection is only preparation and does NOT complete this task.
                                                        
                                After inspecting the minimum necessary files, you MUST use
                                createFile, writeFile or deleteFile to make the required changes.
                                                        
                                Do not finish an IMPLEMENTATION task after only calling
                                listFiles, readFiles or readFile.
                                                        
                                At least one successful project modification is mandatory.
    
                        REVIEW:
                        Inspect the relevant project files.
                        If problems are found, fix them.
                        If everything is already correct,
                        inspection alone is acceptable.
    
                        BUILD_FIX:
                        You MUST modify project files required to
                        resolve the build failure.
                        
                                VISUAL_FIX:
                                You MUST inspect the reported visual defect and modify
                                project files to resolve its implementation root cause.
                                                        
                                Inspection alone is not sufficient.
                                                        
                                At least one successful createFile, writeFile or
                                deleteFile operation is mandatory.
    
                        IMPORTANT:
                        - Actually perform the assigned task.
                        - Use the project tools.
                        -- Call listFiles at most once if needed.
                                 - Do not repeatedly read the same file.
                                 - Only read files that actually exist.
                                 
                                 - For IMPLEMENTATION tasks, inspection must be followed
                                   by an actual project modification.
                                 
                                 - Once you understand the relevant files, STOP inspecting
                                   and START implementing.
                                 
                                 - Never end an IMPLEMENTATION task immediately after
                                   listFiles, readFiles or readFile.
                                 
                                 - Use createFile only for genuinely new files.
                                 - Use writeFile for existing files.
                                 - Use deleteFile only when appropriate.
                                 
                                 - Before finishing an IMPLEMENTATION task, verify that you
                                   actually called createFile, writeFile or deleteFile.
                                 
                                 - Do not merely describe the solution.
                                 - Stop when this task is complete.
    
                        %s
                        """.formatted(
                                task.getTitle(),
                                task.getTaskType().name(),
                                task.getDescription(),
                                userRequest,
                                retryInstruction
                        );


                messages.add(
                        new UserMessage(
                                taskInstruction
                        )
                );


                ChatClient chatClient =
                        chatClientBuilder.build();


                String taskResponse =
                        aiGenerationRetryService.execute(
                                "Agent task: "
                                        + task.getTitle()
                                        + " execution "
                                        + executionAttempt,
                                () -> chatClient
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
    
                                                You must perform real project work
                                                using the available project tools.
    
                                                        For IMPLEMENTATION tasks, inspection alone is NEVER
                                                        sufficient.
                                                                                                        
                                                        An IMPLEMENTATION task has two phases:
                                                                                                        
                                                        1. INSPECT
                                                           Inspect only the minimum files necessary to understand
                                                           the existing implementation.
                                                                                                        
                                                        2. MODIFY
                                                           Immediately use createFile, writeFile or deleteFile
                                                           to implement the requested functionality.
                                                                                                        
                                                        Never finish an IMPLEMENTATION task during phase 1.
                                                                                                        
                                                        If you have already called listFiles/readFiles and understand
                                                        the relevant project structure, your next action should normally
                                                        be createFile or writeFile.
                                                                                                        
                                                        Repeated inspection without implementation is a failed task.
                                                                                                        
                                                        Use createFile for genuinely new files.
                                                        Use writeFile for existing files.
                                                        Use deleteFile only when required.
                                                                                                        
                                                        Before completing an IMPLEMENTATION task, ensure you have
                                                        actually modified the project.
    
                                                Never claim implementation is complete
                                                without actually changing the project.
    
                                                Maintain production-quality UI/UX for
                                                visual applications.
    
                                                Keep layouts responsive and ensure
                                                styling is actually connected to the
                                                application.
    
                                                Stop using tools when the assigned
                                                task has actually been completed.
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
                                                        task.getId()
                                                )
                                        )

                                        .call()

                                        .content()
                        );


                boolean madeProjectChanges =
                        agentToolCallService
                                .hasSuccessfulModification(
                                        task.getId()
                                );

                boolean usedAnyTool =
                        agentToolCallService
                                .hasAnySuccessfulToolCall(
                                        task.getId()
                                );


                System.out.println(
                        "[AGENT] Task "
                                + task.getId()
                                + " verification"
                                + " | type="
                                + task.getTaskType()
                                + " | usedTool="
                                + usedAnyTool
                                + " | modifiedProject="
                                + madeProjectChanges
                );


                switch (task.getTaskType()) {

                    case IMPLEMENTATION, BUILD_FIX, VISUAL_FIX -> {

                        if (!madeProjectChanges) {

                            throw new RuntimeException(
                                    task.getTaskType()
                                            + " task made no project changes"
                            );
                        }
                    }

                    case ANALYSIS, REVIEW -> {

                        if (!usedAnyTool) {

                            throw new RuntimeException(
                                    task.getTaskType()
                                            + " task did not inspect the project"
                            );
                        }
                    }
                }


                if (taskResponse == null ||
                        taskResponse.isBlank()) {

                    taskResponse =
                            madeProjectChanges
                                    ? "Task completed through project file changes."
                                    : "Task completed after project inspection.";
                }


                System.out.println(
                        "[AGENT] Task "
                                + task.getId()
                                + " accepted on execution attempt "
                                + executionAttempt
                );

                return taskResponse;

            } catch (RuntimeException e) {

                lastException = e;
                previousRejectionReason = e.getMessage();

                System.err.println(
                        "[AGENT] Task "
                                + task.getId()
                                + " rejected on execution attempt "
                                + executionAttempt
                                + "/"
                                + MAX_TASK_EXECUTION_ATTEMPTS
                                + ": "
                                + e.getMessage()
                );

                if (executionAttempt ==
                        MAX_TASK_EXECUTION_ATTEMPTS) {

                    break;
                }
            }
        }


        throw new RuntimeException(
                "Task failed after "
                        + MAX_TASK_EXECUTION_ATTEMPTS
                        + " execution attempts: "
                        + task.getTitle(),
                lastException
        );
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
                            nextTaskOrder,
                            AgentTaskType.BUILD_FIX
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
                
                        Diagnose the failure and modify the project
                        so that the build succeeds.
                
                        Original user request:
                        %s
                
                        Build attempt:
                        %d
                
                        Build output:
                        %s
                
                        Build error:
                        %s
                
                        IMPORTANT WORKFLOW:
                
                        1. Determine which existing files are most likely
                           responsible for the build error.
                
                        2. If project structure is needed, call
                           listFiles at most once.
                
                        3. If multiple files need inspection,
                           use readFiles once with all relevant paths.
                
                        4. Use readFile only when exactly one additional
                           file genuinely needs inspection.
                
                        5. Never repeatedly read the same file.
                
                        6. Never use readFile merely to check
                           whether a file exists.
                
                        7. Modify existing files using writeFile.
                
                        8. Use createFile only when a genuinely
                           required file does not exist.
                
                        9. Make the minimum changes necessary
                           to fix the build.
                
                        10. Actually fix the project.
                            Do not merely explain the error.
                
                        11. Preserve unrelated functionality.
                
                        12. Stop once the build problem has been fixed.
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
                        aiGenerationRetryService.execute(
                                "Build fix attempt " + attempt,
                                () ->
                        chatClient
                                .prompt()

                                .system(
                                        """
                                        You are the build-fix agent for a professional
                                        AI application builder.
                                
                                        Your responsibility is to diagnose compiler,
                                        dependency, syntax, import, configuration and
                                        build errors and modify the project to fix them.
                                
                                        AVAILABLE TOOLS
                                
                                        listFiles:
                                        Inspect project structure once.
                                
                                        readFiles:
                                        Preferred tool for reading multiple related files.
                                
                                        readFile:
                                        Read exactly one additional file when necessary.
                                
                                        createFile:
                                        Create a genuinely missing file.
                                
                                        writeFile:
                                        Fix an existing file.
                                
                                        deleteFile:
                                        Delete a file only when necessary.
                                
                                        PREFERRED WORKFLOW
                                
                                        Build error
                                            ->
                                        identify likely files
                                            ->
                                        readFiles
                                            ->
                                        diagnose
                                            ->
                                        writeFile/createFile
                                            ->
                                        stop
                                
                                        IMPORTANT:
                                
                                        Do not repeatedly inspect the same files.
                                
                                        Do not use readFile as an existence check.
                                
                                        Batch related reads using readFiles.
                                
                                        Make the minimum changes necessary to restore
                                        a successful build.
                                
                                        Do not merely explain the error.
                                
                                        Your goal is to leave the project in a
                                        buildable state.
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

                                .content()
                        );

                boolean fixMadeChanges =
                        agentToolCallService
                                .hasSuccessfulModification(
                                        fixTask.getId()
                                );

                boolean fixUsedTools =
                        agentToolCallService
                                .hasAnySuccessfulToolCall(
                                        fixTask.getId()
                                );


                System.out.println(
                        "[BUILD-FIX] Task "
                                + fixTask.getId()
                                + " | usedTool="
                                + fixUsedTools
                                + " | modifiedProject="
                                + fixMadeChanges
                );


                if (!fixMadeChanges) {

                    throw new RuntimeException(
                            "Build-fix agent did not modify any project files. "
                                    + "The build cannot be considered fixed."
                    );
                }


                if (fixResponse == null ||
                        fixResponse.isBlank()) {

                    fixResponse =
                            "Build fix completed through project tool changes.";
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

    private String formatVisualIssues(
            List<String> issues
    ) {

        if (issues == null ||
                issues.isEmpty()) {

            return "No individual issues were provided.";
        }


        StringBuilder builder =
                new StringBuilder();


        for (int i = 0;
             i < issues.size();
             i++) {

            builder
                    .append(i + 1)
                    .append(". ")
                    .append(
                            safeVisualText(
                                    issues.get(i)
                            )
                    )
                    .append("\n");
        }


        return builder.toString().trim();
    }


    private String safeVisualText(
            String value
    ) {

        if (value == null ||
                value.isBlank()) {

            return "Not provided.";
        }

        return value.trim();
    }

    public record AgentResult(
            Long agentRunId,
            String response
    ) {
    }
}