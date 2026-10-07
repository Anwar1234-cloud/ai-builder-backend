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

                currentBuild =
                        buildService
                                .markVisualValidationPassed(
                                        currentBuild.getId()
                                );


                System.out.println(
                        "[VISUAL] Visual validation PASSED "
                                + "for build "
                                + currentBuild.getId()
                );

                return currentBuild;
            }
            currentBuild =
                    buildService
                            .markVisualValidationFailed(
                                    currentBuild.getId()
                            );



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
                                                        When using Tailwind CSS, the complete styling
                                                        toolchain MUST be internally consistent.
                                                                                                        
                                                        For Tailwind CSS v3 projects:
                                                        - package.json must contain compatible Tailwind,
                                                          PostCSS and Autoprefixer dependencies;
                                                        - tailwind.config.js must contain Tailwind content,
                                                          theme and plugin configuration;
                                                        - postcss.config.js must configure PostCSS plugins
                                                          such as tailwindcss and autoprefixer;
                                                        - postcss.config.js must NEVER contain a copied
                                                          Tailwind theme/content configuration;
                                                        - the application's stylesheet may use the
                                                          @tailwind base, components and utilities
                                                          directives;
                                                        - that stylesheet must be imported from the
                                                          application entry/component tree.
                                                                                                        
                                                        Do not confuse tailwind.config.js with
                                                        postcss.config.js.
                                                                                                        
                                                        When creating framework configuration files,
                                                        verify that each file contains configuration for
                                                        the tool represented by its filename.
    
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

            System.out.println(
                    "[BUILD] Starting build attempt "
                            + attempt
                            + "/"
                            + MAX_BUILD_ATTEMPTS
            );

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

            System.out.println(
                    "[BUILD] Build "
                            + latestBuild.getId()
                            + " finished with status "
                            + latestBuild.getStatus()
            );

            if ("SUCCESS".equals(
                    latestBuild.getStatus().name()
            )) {

                return latestBuild;
            }

            if (attempt == MAX_BUILD_ATTEMPTS) {

                return latestBuild;
            }

            int nextTaskOrder =
                    tasks.stream()
                            .map(AgentTask::getTaskOrder)
                            .filter(java.util.Objects::nonNull)
                            .max(Integer::compareTo)
                            .orElse(0)
                            + 1;

            String buildOutput =
                    latestBuild.getOutput() == null
                            ? ""
                            : latestBuild.getOutput();

            String buildErrors =
                    latestBuild.getErrorOutput() == null
                            ? ""
                            : latestBuild.getErrorOutput();

            AgentTask fixTask =
                    agentTaskService.createTask(
                            agentRunId,

                            "Fix build errors - attempt "
                                    + attempt,

                            """
                            Fix the current project build failure.
    
                            Build command:
                            npm run build
    
                            Build output:
                            %s
    
                            Build errors:
                            %s
    
                            This is a BUILD_FIX task.
    
                            You MUST inspect the relevant existing project
                            configuration/source files and modify the project
                            so that the reported build errors are resolved.
    
                            Do not merely explain the errors.
    
                            A successful BUILD_FIX requires an actual
                            createFile, writeFile or deleteFile operation.
                            """.formatted(
                                    buildOutput,
                                    buildErrors
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
                            // Ignore conversation system messages.
                        }
                    }
                }

                String fixInstruction =
                        """
                        The project build failed.
    
                        Perform BUILD_FIX attempt %d.
    
                        Original user request:
                        %s
    
                        ==================================================
                        BUILD OUTPUT
                        ==================================================
    
                        %s
    
                        ==================================================
                        BUILD ERRORS
                        ==================================================
    
                        %s
    
                        ==================================================
                        REQUIRED WORKFLOW
                        ==================================================
    
                        1. Read the actual compiler/build errors first.
    
                        2. Identify the exact files implicated by those errors.
    
                        3. Inspect only the minimum files necessary.
    
                        4. If project structure is necessary, call listFiles
                           at most once.
    
                        5. Prefer readFiles when several related files must
                           be inspected.
    
                        6. Inspect package.json whenever the error involves:
                           - package exports
                           - dependency versions
                           - missing packages
                           - Tailwind
                           - PostCSS
                           - Vite plugins
                           - framework configuration
    
                        7. Inspect the exact source file named by the compiler
                           whenever one is provided.
    
                        8. After identifying the root cause, MODIFY the project.
    
                        9. Reconcile ALL errors visible in this build output
                           during the same repair attempt when they share a
                           common cause.
    
                        10. Do not repeatedly inspect files after the cause
                            is understood.
    
                        11. Preserve unrelated functionality and design.
    
                        12. Stop after implementing the repair.
    
                        ==================================================
                        KNOWN FAILURE PATTERNS
                        ==================================================
    
                        MISSING_EXPORT / NOT_EXPORTED:
    
                        If the build says that a symbol is not exported by
                        an installed package:
    
                        - inspect package.json and the exact importing file;
                        - treat the compiler message as authoritative;
                        - do NOT keep importing the same unsupported symbol;
                        - replace it with a valid export from the installed
                          package version, use another already-installed
                          compatible solution, or remove the unsupported
                          dependency usage if appropriate;
                        - update every affected import and usage consistently;
                        - do not guess that an export exists.
    
                        Example pattern:
    
                        "X is not exported by package Y"
    
                        means the current import from Y is invalid for the
                        installed dependency and must be changed.
    
    
                        TAILWIND / CSS DIRECTIVE / POSTCSS FAILURE:
    
                        If the build reports errors such as:
    
                        "Unknown at rule: @tailwind"
    
                        or reports Tailwind/PostCSS/plugin configuration
                        problems:
    
                        - inspect package.json;
                        - inspect the stylesheet containing the directives;
                        - inspect Vite/PostCSS/Tailwind configuration files
                          if they exist;
                        - determine the ACTUAL installed Tailwind version and
                          current project configuration;
                        - make the stylesheet syntax, package dependencies,
                          PostCSS/Vite integration and Tailwind version
                          consistent with one another;
                        - do NOT blindly preserve @tailwind directives when
                          the installed setup does not process them;
                        - do NOT blindly add configuration for a different
                          Tailwind major version;
                        - do NOT rewrite the entire UI merely to silence a
                          CSS processing error.
    
    
                        IMPORT / MODULE RESOLUTION FAILURE:
    
                        If a module or local import cannot be resolved:
    
                        - inspect the importing file;
                        - inspect package.json for external dependencies;
                        - inspect actual project paths for local imports;
                        - correct the import/path/dependency rather than
                          creating arbitrary duplicate files.
    
    
                        SYNTAX / JSX FAILURE:
    
                        If the compiler gives a file and line number:
    
                        - inspect that exact file first;
                        - repair the smallest coherent code region;
                        - preserve surrounding behavior.
    
    
                        MULTIPLE BUILD ERRORS:
    
                        The build may contain more than one independent error.
    
                        Do not fix only the first error when the output already
                        clearly identifies additional failures.
    
                        Fix all clearly identified failures that can be safely
                        addressed in this repair attempt.
    
                        ==================================================
                        COMPLETION REQUIREMENT
                        ==================================================
    
                        Inspection alone is NOT a BUILD_FIX.
    
                        You MUST successfully call at least one of:
    
                        - writeFile
                        - createFile
                        - deleteFile
    
                        before completing this task.
    
                        The goal is actual corrected project files, not a
                        textual explanation.
                        """.formatted(
                                attempt,
                                userRequest,
                                buildOutput,
                                buildErrors
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
                                                        You are the build-repair agent
                                                        of a professional AI application
                                                        builder.
    
                                                        Your job is to convert a FAILED
                                                        generated project into a BUILDABLE
                                                        project by modifying its files.
    
                                                        You repair:
    
                                                        - compiler errors
                                                        - invalid imports
                                                        - unsupported package exports
                                                        - dependency mismatches
                                                        - Tailwind integration
                                                        - PostCSS configuration
                                                        - Vite configuration
                                                        - CSS processing failures
                                                        - JSX/JavaScript syntax
                                                        - module resolution
                                                        - missing configuration files
    
                                                        AVAILABLE TOOLS:
    
                                                        listFiles
                                                        readFiles
                                                        readFile
                                                        createFile
                                                        writeFile
                                                        deleteFile
    
                                                        REQUIRED PROCESS:
    
                                                        build error
                                                            ->
                                                        identify exact failure
                                                            ->
                                                        inspect implicated source/config
                                                            ->
                                                        determine root cause
                                                            ->
                                                        modify project
                                                            ->
                                                        stop
    
                                                        RULES:
    
                                                        1. Compiler/build output is
                                                           authoritative.
    
                                                        2. Never repeatedly apply the same
                                                           repair if the same error survives
                                                           into another build attempt.
    
                                                        3. When an installed package says an
                                                           export does not exist, do not keep
                                                           importing that export.
    
                                                        4. For dependency-related failures,
                                                           inspect package.json before deciding
                                                           the repair.
    
                                                        5. For Tailwind/PostCSS/CSS processing
                                                           failures, inspect the installed
                                                           dependency versions and actual
                                                           configuration before changing CSS.
    
                                                        6. Never assume Tailwind syntax from
                                                           one major version is correct for
                                                           another major version.
    
                                                        7. When multiple errors are already
                                                           visible, repair all clearly identified
                                                           causes instead of intentionally
                                                           leaving known failures behind.
    
                                                        8. Prefer modifying existing files with
                                                           writeFile.
    
                                                        9. Use createFile only when a genuinely
                                                           required file is missing.
    
                                                        10. Do not create duplicate components
                                                            to avoid fixing existing code.
    
                                                        11. Preserve the user's intended design
                                                            and unrelated functionality.
    
                                                        12. Do not merely describe the fix.
    
                                                        13. BUILD_FIX is complete only after
                                                            project files have actually changed.
    
                                                        14. Stop tool usage once the required
                                                            repair has been implemented.
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
                            "Build-fix agent did not modify any "
                                    + "project files. The build cannot "
                                    + "be considered fixed."
                    );
                }

                if (fixResponse == null ||
                        fixResponse.isBlank()) {

                    System.out.println(
                            "[BUILD-FIX] Gemini returned no text "
                                    + "response, but project modifications "
                                    + "were recorded."
                    );
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