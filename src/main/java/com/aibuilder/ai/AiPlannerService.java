package com.aibuilder.ai;

import com.aibuilder.ai.dto.AgentPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AiPlannerService {

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    public AgentPlan createPlan(
            Long projectId,
            String userRequest
    ) {

        if (userRequest == null || userRequest.isBlank()) {
            throw new IllegalArgumentException(
                    "User request cannot be empty"
            );
        }

        String prompt = """
        You are the planning agent of an AI application builder.

        The builder can create:
        - Websites
        - Web applications
        - Mobile applications
        - Full-stack applications

        Your job is to create a practical execution plan that produces
        BOTH working functionality AND a polished professional user experience.

        IMPORTANT:
        The final result must NOT look like a basic demo, plain HTML page,
        or automatically generated template.

        UI/UX QUALITY IS A FIRST-CLASS REQUIREMENT.

        For projects containing a user interface, the plan MUST account for:

        - Visual hierarchy
        - Responsive layout
        - Typography
        - Color system
        - Spacing system
        - Navigation
        - Hero/header when appropriate
        - Clear calls to action
        - Cards and content sections when appropriate
        - Forms and interactive elements when appropriate
        - Icons
        - Images/visual content when appropriate
        - Hover states
        - Subtle transitions/animations when appropriate
        - Mobile, tablet and desktop layouts
        - Accessibility and readable contrast
        - Consistent component styling
        - Professional overall visual polish

        The visual design MUST match the user's project/domain.

        Examples:

        Restaurant:
        premium food imagery, elegant typography, menu sections,
        reservations, testimonials, location/contact.

        Coffee shop:
        warm branding, product/menu cards, story/about section,
        location, CTA, testimonials and polished navigation.

        SaaS:
        product-focused hero, features, product preview,
        pricing, testimonials, FAQ and strong CTA.

        E-commerce:
        product presentation, categories, product cards,
        offers, reviews, navigation and checkout-oriented UX.

        IMPORTANT PLANNING RULES:

        1. Return ONLY valid JSON.
        2. Do not use markdown.
        3. Do not add explanations outside JSON.
        4. Create practical implementation tasks.
        5. Tasks must describe actual work the execution agent must perform.
        6. Tasks must be in dependency/execution order.
        7. taskOrder must start at 1 and increase sequentially.
        8. Avoid unnecessary tasks.
        9. Do not merely describe what should happen.
        10. The plan must lead to actual code changes.

        TASK TYPES:

        Every task MUST include a taskType.

        taskType MUST be exactly one of:

        ANALYSIS
        IMPLEMENTATION
        REVIEW

        ANALYSIS:
        Use only when the primary purpose is to inspect, understand,
        analyze or establish direction from the existing project.

        Examples:
        - Inspect existing project structure.
        - Determine the current architecture.
        - Establish UI/UX direction from the existing application.

        ANALYSIS tasks may inspect files without modifying them.

        IMPLEMENTATION:
        Use whenever the task requires actual project changes.

        This includes:
        - Creating files.
        - Modifying files.
        - Deleting files.
        - Building UI components.
        - Creating pages.
        - Styling the application.
        - Adding responsive behavior.
        - Adding functionality.
        - Integrating APIs.
        - Configuring dependencies.
        - Fixing application code.
        - Connecting CSS or assets.
        - Implementing frontend or backend features.

        Most project-building tasks MUST be IMPLEMENTATION.

        An IMPLEMENTATION task is not complete unless actual project
        files are successfully created, modified or deleted.

        REVIEW:
        Use only when the primary purpose is final inspection,
        quality review or verification.

        Examples:
        - Responsive review.
        - Accessibility review.
        - Visual consistency review.
        - Final UI/UX review.

        A REVIEW task may inspect without modifying files if no
        problems are found.

        If a REVIEW task discovers a problem that can be corrected,
        it should modify the appropriate project files.

        IMPORTANT:

        Do NOT classify implementation work as ANALYSIS or REVIEW.

        If a task includes both review and actual improvement,
        and project modifications are expected, classify it as
        IMPLEMENTATION.

        REQUIRED PLAN STRUCTURE FOR UI PROJECTS:

        The plan should normally follow this progression:

        Task 1:
        Analyze the existing project and establish the UI/UX direction,
        design language, layout structure, responsive strategy and
        visual hierarchy for the requested product.

        Task 2:
        Implement or improve the main application/page structure,
        including navigation, hero/header and major layout sections
        appropriate to the domain.

        Task 3:
        Implement the main content/components and functionality.

        Task 4:
        Apply visual styling, spacing, typography, colors, imagery,
        cards, buttons, icons and interactions.

        Task 5:
        Review responsive behavior and polish the interface for
        mobile, tablet and desktop.

        Task 6:
        Review the complete UI for consistency, obvious visual problems,
        broken sections, poor spacing, readability and missing UX details,
        and improve it.

        Then include any remaining functional tasks and finally the
        build/verification work when appropriate.

        IMPORTANT:
        Do not blindly copy the examples above.
        Adapt the design and task structure to the user's actual request.

        REQUIRED JSON FORMAT:

        {
          "tasks": [
            {
              "title": "Analyze project and define UI/UX direction",
              "description": "Inspect the existing project and establish the visual language, layout hierarchy, responsive strategy and component structure required for the requested product.",
              "taskOrder": 1,
              "taskType": "ANALYSIS"
            },
            {
              "title": "Implement the application structure",
               "description": "Create or modify the required project files and implement the main application structure, navigation, hero and primary sections.",
               "taskOrder": 2,
               "taskType": "IMPLEMENTATION"
            },
            {
              "title": "Review responsive behavior and visual quality",
              "description": "Inspect the completed interface for responsive behavior, consistency, accessibility and obvious visual defects. Correct discovered problems when necessary.",
              "taskOrder": 3,
              "taskType": "REVIEW"
            }
          ]
        }

        Project ID:
        %d

        User request:
        %s
        """.formatted(projectId, userRequest);

        ChatClient chatClient =
                chatClientBuilder.build();

        String response = generateWithRetry(
                chatClient,
                prompt
        );

        if (response == null || response.isBlank()) {
            throw new RuntimeException(
                    "AI planner returned an empty response"
            );
        }

        return parsePlan(response);
    }

    private String generateWithRetry(
            ChatClient chatClient,
            String prompt
    ) {

        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            try {

                return chatClient
                        .prompt()
                        .system("""
    You are a senior product architect and UI/UX planning agent
    for an AI application builder.

    Create minimal, logical and executable plans.

    Every task MUST include a taskType.

    Valid taskType values are exactly:

    ANALYSIS
    IMPLEMENTATION
    REVIEW

    ANALYSIS is for inspection, understanding and planning.

    IMPLEMENTATION is for any work that creates, modifies,
    deletes, styles, configures or integrates project files.

    REVIEW is for final quality inspection and verification.

    Most tasks involved in actually building a project should
    be IMPLEMENTATION.

    Never classify implementation work as ANALYSIS or REVIEW
    simply to avoid making project changes.

    For any project containing a user interface:
    - Treat UI/UX quality as mandatory.
    - Plan design before implementation.
    - Plan responsive behavior.
    - Plan visual hierarchy.
    - Plan polished interactions and component consistency.
    - Adapt the design language to the project's domain.
    - Never assume that merely functional UI is sufficient.

    The execution agent will use your plan directly,
    so every task must describe concrete work that can actually
    be implemented in project files.

    Always return valid JSON matching the requested schema.
    """)
                        .user(prompt)
                        .call()
                        .content();

            } catch (Exception e) {

                if (attempt == maxAttempts) {
                    throw new RuntimeException(
                            "Gemini planner failed after "
                                    + maxAttempts
                                    + " attempts: "
                                    + e.getMessage(),
                            e
                    );
                }

                long delay =
                        switch (attempt) {
                            case 1 -> 2000L;
                            case 2 -> 5000L;
                            default -> 10000L;
                        };

                System.out.println(
                        "Gemini request failed. Retrying in "
                                + delay
                                + " ms. Attempt "
                                + (attempt + 1)
                                + "/"
                                + maxAttempts
                );

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interruptedException) {

                    Thread.currentThread().interrupt();

                    throw new RuntimeException(
                            "Gemini retry interrupted",
                            interruptedException
                    );
                }
            }
        }

        throw new RuntimeException("Unexpected retry failure");
    }

    private AgentPlan parsePlan(String response) {

        String json = response.trim();

        
        if (json.startsWith("```")) {
            json = json
                    .replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
        }

        try {

            AgentPlan plan =
                    objectMapper.readValue(
                            json,
                            AgentPlan.class
                    );

            validatePlan(plan);

            return plan;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to parse AI planner response: "
                            + response,
                    e
            );
        }
    }

    private void validatePlan(AgentPlan plan) {

        if (plan == null ||
                plan.tasks() == null ||
                plan.tasks().isEmpty()) {

            throw new RuntimeException(
                    "AI planner returned no tasks"
            );
        }

        int expectedOrder = 1;

        for (AgentPlan.PlannedTask task : plan.tasks()) {

            if (task == null ||
                    task.title() == null ||
                    task.title().isBlank()) {

                throw new RuntimeException(
                        "AI planner returned a task without a title"
                );
            }

            if (task.description() == null ||
                    task.description().isBlank()) {

                throw new RuntimeException(
                        "AI planner returned a task without a description"
                );
            }
            if (task.taskType() == null ||
                    task.taskType().isBlank()) {

                throw new RuntimeException(
                        "AI planner returned a task without a taskType"
                );
            }

            String taskType =
                    task.taskType()
                            .trim()
                            .toUpperCase();

            if (!taskType.equals("ANALYSIS") &&
                    !taskType.equals("IMPLEMENTATION") &&
                    !taskType.equals("REVIEW")) {

                throw new RuntimeException(
                        "Invalid taskType returned by AI planner: "
                                + task.taskType()
                );
            }

            if (task.taskOrder() == null ||
                    task.taskOrder() != expectedOrder) {

                throw new RuntimeException(
                        "Invalid task order returned by AI planner"
                );
            }

            expectedOrder++;
        }
    }
}