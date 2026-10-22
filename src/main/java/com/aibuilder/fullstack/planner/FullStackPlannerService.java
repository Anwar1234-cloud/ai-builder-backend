
package com.aibuilder.fullstack.planner;

import com.aibuilder.fullstack.dto.FullStackPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class FullStackPlannerService {

    private static final int MAX_ATTEMPTS = 3;

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final FullStackPlanValidator validator;

    public FullStackPlan createPlan(String userRequest) {

        if (userRequest == null || userRequest.isBlank()) {
            throw new IllegalArgumentException(
                    "User request cannot be empty"
            );
        }

        String prompt = """
                Design a complete full-stack application architecture.

                USER REQUEST:
                %s

                TECHNOLOGY STACK:
                - Frontend: React
                - Backend: Spring Boot
                - Database: PostgreSQL

                Your architecture must be DOMAIN-INDEPENDENT.

                Examples of supported domains:
                - E-commerce
                - Hospital management
                - Restaurant management
                - Booking systems
                - Education platforms
                - SaaS applications
                - Real estate
                - Inventory management
                - Social applications

                Do not assume that every application needs
                products, orders, payments or an admin dashboard.

                Infer the required entities, relationships,
                API endpoints, pages, roles and features
                from the user's actual requirements.

                ARCHITECTURE RULES:

                1. Design realistic domain entities.
                2. Use Java-compatible field types:
                   String, Long, Integer, Boolean,
                   BigDecimal, LocalDate, LocalDateTime,
                   Double and UUID.
                3. Do not include an ID field in entity fields.
                   Generated entities will receive their own IDs.
                4. Use PascalCase for entity names.
                5. Use camelCase for field names.
                6. Use RESTful API paths starting with /api/.
                7. Define appropriate HTTP methods.
                8. Include authentication only when appropriate.
                9. Define roles and permissions when needed.
                10. Connect frontend pages to the relevant APIs.
                11. Define entity relationships explicitly.
                12. Keep the architecture practical and coherent.
                13. Do not generate Java or React source code.
                14. Return ONLY valid JSON.
                15. Do not include markdown or explanations.

                RELATIONSHIP RULES:

                Every relationship MUST contain these exact properties:

                - sourceEntity
                - targetEntity
                - relationshipType
                - fieldName

                The property "relationshipType" is mandatory.

                Allowed relationshipType values:
                - ONE_TO_ONE
                - ONE_TO_MANY
                - MANY_TO_MANY

                Never use "type", "relationType", "cardinality",
                or "relationship" instead of "relationshipType".

                Both sourceEntity and targetEntity must match
                names defined in the entities array.

                Example:

                {
                  "sourceEntity": "Doctor",
                  "targetEntity": "Appointment",
                  "relationshipType": "ONE_TO_MANY",
                  "fieldName": "appointments"
                }

                Use an empty relationships array if none are needed.
                
                FUNCTIONAL TEST PLANNING RULES:
                                
                1. Generate functional tests for important application workflows.
                2. Use only endpoints defined in the apis array.
                3. Include valid request bodies for POST, PUT and PATCH.
                4. Define the expected HTTP status for each test.
                5. Include expected response fields when applicable.
                6. Respect authentication requirements and roles.
                7. Cover registration and login if authentication is required
                   and those endpoints are defined in the API plan.
                8. Cover CRUD operations when required by the application.
                9. Keep test data consistent with entity field definitions.
                10. Do not invent endpoints.
                11. Never include real credentials or secrets.
                12. Use an empty functionalTests array if no tests apply.
                13. Generate functional tests in dependency order.
                14. Every test must have a unique name.
                15. Use dependsOn to declare prerequisite test names.
                16. Use extractVariables to capture values from JSON responses.
                17. Use ${variableName} placeholders to reuse captured values
                    in endpoints, request bodies and expected response fields.
                18. For authentication, generate registration/login tests
                    when the corresponding APIs exist.
                19. Extract the login token using extractVariables.
                20. Set authTokenVariable for authenticated tests.
                21. For CRUD workflows, capture IDs from create responses
                    and reuse them in read, update and delete endpoints.
                22. Never use undefined variables or circular dependencies.
                23. Never generate real credentials.
                24. All tests must be compatible with the defined API contracts.

                REQUIRED JSON STRUCTURE:

                {
                  "applicationName": "ExampleApp",
                  "description": "Application description",
                  "frontendFramework": "React",
                  "backendFramework": "Spring Boot",
                  "database": "PostgreSQL",
                  "authenticationRequired": true,
                  "roles": [
                    {
                      "name": "USER",
                      "description": "Regular application user",
                      "permissions": ["READ_OWN_DATA"]
                    }
                  ],
                  "entities": [
                    {
                      "name": "ExampleEntity",
                      "description": "Domain entity",
                      "fields": [
                        {
                          "name": "name",
                          "type": "String",
                          "required": true,
                          "unique": false,
                          "description": "Entity name"
                        }
                      ]
                    }
                  ],
                  "relationships": [
                    {
                      "sourceEntity": "ExampleEntity",
                      "targetEntity": "ExampleEntity",
                      "relationshipType": "ONE_TO_MANY",
                      "fieldName": "relatedExamples"
                    }
                  ],
                  "apis": [
                    {
                      "method": "GET",
                      "path": "/api/examples",
                      "purpose": "List examples",
                      "requestEntity": null,
                      "responseEntity": "ExampleEntity",
                      "authenticationRequired": false,
                      "requiredRole": null
                    }
                  ],
                  "pages": [
                    {
                      "name": "Home",
                      "route": "/",
                      "description": "Application home page",
                      "authenticationRequired": false,
                      "requiredRoles": [],
                      "apiPaths": ["/api/examples"]
                    }
                  ],
                  "features": [
                    {
                      "name": "Example Management",
                      "description": "Manage example records",
                      "relatedEntities": ["ExampleEntity"],
                      "relatedPages": ["Home"]
                    }
                  ],
                "functionalTests": [
                  {
                    "name": "List examples",
                    "description": "Verify examples can be retrieved",
                    "method": "GET",
                    "endpoint": "/api/examples",
                    "requestBody": null,
                    "expectedStatus": 200,
                    "authenticationRequired": false,
                    "requiredRole": null,
                    "expectedResponseFields": {},
                    "dependsOn": [],
                    "extractVariables": {},
                    "authTokenVariable": null
                  }
                ]
                }

                IMPORTANT:
                The example illustrates the JSON schema only.
                Generate an architecture specific to the user's request.

                Include all top-level properties.
                Use empty arrays when no items are needed.
                Use null for optional missing values.
                """.formatted(userRequest);

        ChatClient chatClient = chatClientBuilder.build();

        String currentPrompt = prompt;
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {

            String response;

            try {
                response = chatClient
                        .prompt()
                        .system("""
                    You are a senior full-stack software architect.

                    Design applications using React,
                    Spring Boot and PostgreSQL.

                    Return only valid JSON matching
                    the required schema.

                    Every relationship must have:
                    sourceEntity, targetEntity,
                    relationshipType and fieldName.

                    Allowed relationshipType values:
                    ONE_TO_ONE, ONE_TO_MANY, MANY_TO_MANY.

                    Do not generate source code.
                    """)
                        .user(currentPrompt)
                        .call()
                        .content();

            } catch (Exception exception) {

                System.err.println(
                        "[FULLSTACK] AI model call failed on attempt "
                                + attempt
                );

                exception.printStackTrace();

                throw new IllegalStateException(
                        "Full-stack architecture AI request failed: "
                                + exception.getMessage(),
                        exception
                );
            }

            if (response == null || response.isBlank()) {
                lastException = new IllegalStateException(
                        "Planner returned an empty response"
                );
                currentPrompt = prompt + """

                        The previous response was empty.
                        Return a complete valid JSON architecture.
                        """;
                continue;
            }

            String json = removeMarkdownFences(response);

            try {

                FullStackPlan plan = objectMapper.readValue(
                        json,
                        FullStackPlan.class
                );

                validator.validate(plan);

                return plan;

            } catch (Exception exception) {

                lastException = exception;

                System.err.println(
                        "Full-stack plan validation failed, attempt "
                                + attempt + ": "
                                + exception.getMessage()
                );

                // Limit logging to local debugging. Do not log
                // full AI responses in production.
                if (attempt == MAX_ATTEMPTS) {
                    System.err.println(
                            "Last AI response:\n" + json
                    );
                }

                currentPrompt = prompt + """

                        YOUR PREVIOUS RESPONSE FAILED VALIDATION.

                        Validation error:
                        """ + exception.getMessage() + """

                        
                        Generate the complete JSON architecture again.

                        Correct the validation error.

                        Do not omit any required fields.
                        Do not return markdown.
                        """;
            }
        }

        throw new IllegalStateException(
                "Failed to generate a valid full-stack architecture "
                        + "after " + MAX_ATTEMPTS + " attempts",
                lastException
        );
    }

    private String removeMarkdownFences(String response) {

        String json = response.trim();

        if (json.startsWith("```")) {
            json = json
                    .replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
        }

        return json;
    }
}
