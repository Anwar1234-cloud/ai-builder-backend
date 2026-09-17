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

                Create a step-by-step implementation plan for the user's request.

                IMPORTANT RULES:
                - Return ONLY valid JSON.
                - Do not use markdown.
                - Do not add explanations outside the JSON.
                - Create practical implementation tasks.
                - Each task must have:
                  title
                  description
                  taskOrder
                - taskOrder must start at 1.
                - Keep tasks in execution order.
                - Do not create unnecessary tasks.
                - The tasks should describe actual work the AI agent needs to perform.

                Required JSON format:

                {
                  "tasks": [
                    {
                      "title": "Analyze project",
                      "description": "Inspect the existing project structure and determine what must change.",
                      "taskOrder": 1
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
                            You are a professional software planning agent.

                            Produce minimal, logical, executable plans.

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