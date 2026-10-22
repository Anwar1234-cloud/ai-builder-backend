
package com.aibuilder.fullstack.planner;

import com.aibuilder.ai.dto.AgentPlan;
import com.aibuilder.fullstack.dto.FullStackPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FullStackTaskPlannerService {

    private final FullStackPlanValidator validator;
    private final ObjectMapper objectMapper;

    public AgentPlan createTaskPlan(FullStackPlan architecture) {

        if (architecture == null) {
            throw new IllegalArgumentException(
                    "Full-stack architecture is required"
            );
        }

        validator.validate(architecture);

        String architectureJson =
                objectMapper.writeValueAsString(architecture);

        List<AgentPlan.PlannedTask> tasks = new ArrayList<>();

        addTask(
                tasks,
                "Set up full-stack project structure",
                """
                Create the full-stack workspace structure.

                Architecture:
                %s

                Required structure:

                frontend/
                  package.json
                  index.html
                  src/
                    main.jsx
                    App.jsx

                backend/
                  pom.xml
                  src/main/java/com/generated/app/
                  src/main/resources/application.yaml

                IMPORTANT:
                - Project files are stored using project-relative paths.
                - Use createFile for new files.
                - Use writeFile for existing files.
                - Never overwrite an existing file blindly.
                - Keep frontend and backend isolated.
                - Use Java 21 and a compatible Spring Boot version.
                - Do not add real database passwords.
                - Create the necessary entry points and configuration.
                - Ensure package declarations match file paths.
                """.formatted(architectureJson),
                "IMPLEMENTATION"
        );

        addTask(
                tasks,
                "Implement PostgreSQL database entities",
                """
                Implement the persistence layer using the
                approved architecture.

                Architecture:
                %s

                Create the required JPA entities and repositories.

                Requirements:
                - Use jakarta.persistence annotations.
                - Give each entity a generated primary key.
                - Implement fields with correct Java types.
                - Respect required and unique constraints.
                - Implement entity relationships.
                - Use appropriate foreign keys and ownership.
                - Avoid infinite JSON serialization cycles.
                - Use PostgreSQL-compatible persistence settings.
                - Do not invent unrelated domain entities.
                - Do not expose password hashes in API responses.
                """.formatted(architectureJson),
                "IMPLEMENTATION"
        );

        if (architecture.authenticationRequired()) {
            addTask(
                    tasks,
                    "Implement authentication and authorization",
                    """
                    Implement application authentication and
                    authorization based on this architecture:

                    %s

                    Requirements:
                    - Implement secure registration and login.
                    - Hash passwords using a supported encoder.
                    - Implement Spring Security.
                    - Protect endpoints according to roles.
                    - Never trust a client-supplied user ID
                      as proof of resource ownership.
                    - Do not hardcode JWT secrets.
                    - Do not return password hashes.
                    - Use environment-based secrets.
                    - Keep public endpoints explicitly public.
                    - Ensure login returns a usable authentication
                      response rather than a raw User entity.
                    """.formatted(architectureJson),
                    "IMPLEMENTATION"
            );
        }

        addTask(
                tasks,
                "Implement backend APIs and business logic",
                """
                Implement the Spring Boot application backend.

                Architecture:
                %s

                Create:
                - Request/response DTOs
                - Repository queries
                - Business services
                - REST controllers
                - Validation
                - Exception handling

                Requirements:
                - Implement the planned API methods and paths.
                - Validate all incoming requests.
                - Enforce authentication and authorization.
                - Apply ownership checks for user-specific data.
                - Prevent invalid domain state transitions.
                - Keep controllers thin.
                - Avoid returning persistence entities directly
                  when sensitive fields or relationships exist.
                - Keep frontend API contracts consistent.
                """.formatted(architectureJson),
                "IMPLEMENTATION"
        );

        addTask(
                tasks,
                "Implement React application pages",
                """
                Implement the frontend based on this architecture:

                %s

                Requirements:
                - Create React pages for planned routes.
                - Implement reusable components.
                - Implement routing and navigation.
                - Connect forms to the planned APIs.
                - Provide loading, error and empty states.
                - Handle authentication when required.
                - Protect routes according to user roles.
                - Create responsive desktop/mobile layouts.
                - Use professional typography and spacing.
                - Use a consistent color and component system.
                - Avoid placeholder-only functionality.
                - Do not hardcode backend credentials.
                """.formatted(architectureJson),
                "IMPLEMENTATION"
        );

        addTask(
                tasks,
                "Integrate frontend and backend",
                """
                Connect the React frontend to the Spring Boot
                backend using the approved architecture.

                %s

                Requirements:
                - Implement a reusable frontend API client.
                - Configure the API base URL using environment
                  variables or appropriate development proxying.
                - Ensure request and response contracts match.
                - Handle authentication tokens safely.
                - Handle HTTP errors.
                - Implement required forms and CRUD interactions.
                - Ensure frontend routes and API paths are distinct.
                - Do not silently simulate successful API calls.
                """.formatted(architectureJson),
                "IMPLEMENTATION"
        );

        addTask(
                tasks,
                "Review full-stack application implementation",
                """
                Review the implemented application against
                the approved architecture.

                %s

                Check:
                - Required entities and relationships
                - API coverage
                - Authentication and authorization
                - Ownership checks
                - React pages and navigation
                - API integration
                - Database configuration
                - Responsive design
                - Missing imports and dependencies
                - Package and directory consistency

                Fix issues found during review using project tools.

                Do not claim that builds or runtime tests passed
                unless they have actually been executed.
                """.formatted(architectureJson),
                "REVIEW"
        );

        return new AgentPlan(List.copyOf(tasks));
    }

    private void addTask(
            List<AgentPlan.PlannedTask> tasks,
            String title,
            String description,
            String taskType
    ) {

        tasks.add(
                new AgentPlan.PlannedTask(
                        title,
                        description,
                        tasks.size() + 1,
                        taskType
                )
        );
    }
}
