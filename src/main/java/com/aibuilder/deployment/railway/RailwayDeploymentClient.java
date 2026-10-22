package com.aibuilder.deployment.railway;

import com.aibuilder.deployment.exception.DeploymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RailwayDeploymentClient {

    private final RailwayProperties properties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

    public JsonNode execute(
            String query,
            Map<String, Object> variables
    ) {
        if (properties.getToken() == null ||
                properties.getToken().isBlank()) {
            throw new DeploymentException(
                    "RAILWAY_TOKEN is not configured."
            );
        }

        try {
            String requestBody = objectMapper.writeValueAsString(
                    Map.of(
                            "query", query,
                            "variables", variables
                    )
            );

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(properties.getApiBaseUrl()))
                            .timeout(Duration.ofSeconds(60))
                            .header(
                                    "Authorization",
                                    "Bearer " + properties.getToken()
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString(requestBody)
                            )
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (response.statusCode() < 200 ||
                    response.statusCode() >= 300) {
                throw new DeploymentException(
                        "Railway API HTTP error: "
                                + response.statusCode()
                );
            }

            JsonNode json =
                    objectMapper.readTree(response.body());

            JsonNode errors = json.get("errors");

            if (errors != null &&
                    errors.isArray() &&
                    !errors.isEmpty()) {
                throw new DeploymentException(
                        "Railway GraphQL error: " + errors
                );
            }

            JsonNode data = json.get("data");

            if (data == null || data.isNull()) {
                throw new DeploymentException(
                        "Railway API returned no data."
                );
            }

            return data;

        } catch (DeploymentException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeploymentException(
                    "Railway API request interrupted.", e
            );
        } catch (Exception e) {
            throw new DeploymentException(
                    "Railway API request failed: "
                            + e.getMessage(), e
            );
        }
    }

    public String createProject(String projectName, String workspaceId) {

        String mutation = """
            mutation CreateProject($input: ProjectCreateInput!) {
                projectCreate(input: $input) {
                    id
                    name
                }
            }
            """;

        Map<String, Object> input = new java.util.HashMap<>();
        input.put("name", projectName);

        if (workspaceId != null && !workspaceId.isBlank()) {
            input.put("workspaceId", workspaceId);
        }

        JsonNode data = execute(
                mutation,
                Map.of("input", input)
        );

        JsonNode project = data.path("projectCreate");

        if (project.isMissingNode()
                || project.path("id").isMissingNode()
                || project.path("id").asText().isBlank()) {

            throw new DeploymentException(
                    "Railway project creation returned no project ID."
            );
        }

        return project.path("id").asText();
    }

    public String createService(
            String projectId,
            String serviceName
    ) {

        String mutation = """
            mutation CreateService($input: ServiceCreateInput!) {
                serviceCreate(input: $input) {
                    id
                    name
                }
            }
            """;

        Map<String, Object> input = Map.of(
                "projectId", projectId,
                "name", serviceName
        );

        JsonNode data = execute(
                mutation,
                Map.of("input", input)
        );

        JsonNode service = data.path("serviceCreate");

        if (service.path("id").isMissingNode()
                || service.path("id").asText().isBlank()) {

            throw new DeploymentException(
                    "Railway service creation returned no service ID."
            );
        }

        return service.path("id").asText();
    }

    public String getEnvironmentId(String projectId) {

        String query = """
            query GetProject($projectId: String!) {
                project(id: $projectId) {
                    environments {
                        edges {
                            node {
                                id
                                name
                            }
                        }
                    }
                }
            }
            """;

        JsonNode data = execute(
                query,
                Map.of("projectId", projectId)
        );

        JsonNode environments = data
                .path("project")
                .path("environments")
                .path("edges");

        if (!environments.isArray() || environments.isEmpty()) {
            throw new DeploymentException(
                    "No Railway environment found for project: "
                            + projectId
            );
        }

        for (JsonNode edge : environments) {
            JsonNode environment = edge.path("node");

            if ("production".equalsIgnoreCase(
                    environment.path("name").asText())) {
                return environment.path("id").asText();
            }
        }

        return environments.get(0)
                .path("node")
                .path("id")
                .asText();
    }

    public void upsertVariable(
            String projectId,
            String environmentId,
            String serviceId,
            String name,
            String value
    ) {

        String mutation = """
            mutation UpsertVariable($input: VariableUpsertInput!) {
                variableUpsert(input: $input)
            }
            """;

        Map<String, Object> input = Map.of(
                "projectId", projectId,
                "environmentId", environmentId,
                "serviceId", serviceId,
                "name", name,
                "value", value,
                "skipDeploys", true
        );

        execute(
                mutation,
                Map.of("input", input)
        );
    }

    public String createDomain(
            String environmentId,
            String serviceId
    ) {

        String mutation = """
            mutation CreateDomain($input: ServiceDomainCreateInput!) {
                serviceDomainCreate(input: $input) {
                    domain
                }
            }
            """;

        Map<String, Object> input = Map.of(
                "environmentId", environmentId,
                "serviceId", serviceId
        );

        JsonNode data = execute(
                mutation,
                Map.of("input", input)
        );

        String domain = data
                .path("serviceDomainCreate")
                .path("domain")
                .asText();

        if (domain.isBlank()) {
            throw new DeploymentException(
                    "Railway did not return a service domain."
            );
        }

        return "https://" + domain;
    }


    public String getExistingDomain(
            String environmentId,
            String serviceId
    ) {
        String query = """
        query GetServiceDomains(
            $environmentId: String!,
            $serviceId: String!
        ) {
            domains(
                environmentId: $environmentId,
                serviceId: $serviceId
            ) {
                serviceDomains {
                    domain
                }
            }
        }
        """;

        JsonNode data = execute(
                query,
                Map.of(
                        "environmentId", environmentId,
                        "serviceId", serviceId
                )
        );

        JsonNode domains = data
                .path("domains")
                .path("serviceDomains");

        if (domains.isArray() && !domains.isEmpty()) {
            String domain = domains.get(0)
                    .path("domain")
                    .asText();

            if (!domain.isBlank()) {
                return "https://" + domain;
            }
        }

        return null;
    }


    public JsonNode getPostgresTemplateConfig() {

        String query = """
        query GetPostgresTemplate {
            template(code: "postgres") {
                id
                name
                code
                isVerified
                isApproved
                serializedConfig
            }
        }
        """;

        JsonNode data = execute(query, Map.of());

        JsonNode template = data.path("template");

        if (template.isMissingNode() || template.isNull()) {
            throw new DeploymentException(
                    "Railway PostgreSQL template was not found."
            );
        }

        JsonNode config = template.path("serializedConfig");

        if (config.isMissingNode() || config.isNull()
                || !config.isObject()) {
            throw new DeploymentException(
                    "Railway PostgreSQL template has no valid configuration."
            );
        }

        JsonNode services = config.path("services");

        if (!services.isObject() || services.isEmpty()) {
            throw new DeploymentException(
                    "Railway PostgreSQL template contains no services."
            );
        }

        return template;
    }

    public JsonNode deployPostgres(
            String projectId,
            String environmentId
    ) {
        JsonNode template = getPostgresTemplateConfig();

        String templateId = template.path("id").asText();
        JsonNode config = template.path("serializedConfig");

        if (templateId.isBlank()) {
            throw new DeploymentException(
                    "PostgreSQL template ID is missing."
            );
        }

        // Only allow the PostgreSQL image we inspected.
        String expectedImage =
                "ghcr.io/railwayapp-templates/postgres-ssl:18";

        JsonNode services = config.path("services");

        if (services.size() != 1) {
            throw new DeploymentException(
                    "Unexpected PostgreSQL template service count."
            );
        }

        JsonNode service = services.properties()
                .iterator()
                .next()
                .getValue();

        String image = service.path("source")
                .path("image").asText();

        String mountPath = service.path("deploy")
                .path("requiredMountPath").asText();

        if (!expectedImage.equals(image)
                || !"/var/lib/postgresql/data".equals(mountPath)) {
            throw new DeploymentException(
                    "PostgreSQL template image or mount path changed."
            );
        }

        if (!service.path("volumeMounts").isObject()
                || service.path("volumeMounts").isEmpty()) {
            throw new DeploymentException(
                    "PostgreSQL template has no persistent volume."
            );
        }

        String mutation = """
    mutation DeployPostgres($input: TemplateDeployV2Input!) {
        templateDeployV2(input: $input) {
            operationId
            projectId
            workflowId
        }
    }
    """;

        Map<String, Object> input = Map.of(
                "projectId", projectId,
                "environmentId", environmentId,
                "templateId", templateId,
                "serializedConfig",
                objectMapper.convertValue(config, Object.class),
                "stageOnly", false
        );

        JsonNode data = execute(
                mutation,
                Map.of("input", input)
        );

        JsonNode deployment = data.path("templateDeployV2");

        if (deployment.isMissingNode() || deployment.isNull()) {
            throw new DeploymentException(
                    "Railway PostgreSQL deployment returned no result."
            );
        }

        String returnedProjectId = deployment.path("projectId").asText();

        if (!projectId.equals(returnedProjectId)) {
            throw new DeploymentException(
                    "Railway PostgreSQL deployment returned an unexpected project ID."
            );
        }

        return deployment;
    }

    public String getPostgresServiceId(String projectId) {

        String query = """
        query GetProjectServices($projectId: String!) {
            project(id: $projectId) {
                services {
                    edges {
                        node {
                            id
                            name
                            projectId
                            templateId
                            templateServiceId
                        }
                    }
                }
            }
        }
        """;

        JsonNode data = execute(
                query,
                Map.of("projectId", projectId)
        );

        JsonNode services = data
                .path("project")
                .path("services")
                .path("edges");

        if (!services.isArray()) {
            throw new DeploymentException(
                    "Railway project services response is invalid."
            );
        }

        String postgresTemplateId =
                "b55da7dc-09be-4140-bc65-1284d15d349c";

        String matchedServiceId = null;

        for (JsonNode edge : services) {

            JsonNode service = edge.path("node");

            String templateId =
                    service.path("templateId").asText();

            if (!postgresTemplateId.equals(templateId)) {
                continue;
            }

            String serviceId = service.path("id").asText();

            if (serviceId.isBlank()) {
                continue;
            }

            if (matchedServiceId != null) {
                throw new DeploymentException(
                        "Multiple PostgreSQL template services found. "
                                + "Cannot identify the correct database automatically."
                );
            }

            matchedServiceId = serviceId;
        }

        if (matchedServiceId == null) {
            throw new DeploymentException(
                    "PostgreSQL template service not found in Railway project."
            );
        }

        return matchedServiceId;
    }

    public JsonNode getDeploymentStatus(String deploymentId) {

        if (deploymentId == null || deploymentId.isBlank()) {
            throw new DeploymentException(
                    "Railway deployment ID is required."
            );
        }

        String query = """
        query GetDeployment($id: String!) {
            deployment(id: $id) {
                id
                status
                serviceId
                environmentId
                createdAt
                updatedAt
            }
        }
        """;

        JsonNode data = execute(
                query,
                Map.of("id", deploymentId)
        );

        JsonNode deployment = data.path("deployment");

        if (deployment.isMissingNode() || deployment.isNull()) {
            throw new DeploymentException(
                    "Railway deployment not found: " + deploymentId
            );
        }

        String status = deployment.path("status").asText();

        if (status.isBlank()) {
            throw new DeploymentException(
                    "Railway deployment status is missing."
            );
        }

        return deployment;
    }

    public JsonNode getLatestDeployment(
            String projectId,
            String environmentId,
            String serviceId
    ) {

        if (projectId == null || projectId.isBlank()
                || environmentId == null || environmentId.isBlank()
                || serviceId == null || serviceId.isBlank()) {

            throw new DeploymentException(
                    "Project ID, environment ID and service ID are required."
            );
        }

        String query = """
        query GetDeployments(
            $input: DeploymentListInput!,
            $first: Int!,
            $after: String
        ) {
            deployments(
                input: $input,
                first: $first,
                after: $after
            ) {
                edges {
                    node {
                        id
                        status
                        serviceId
                        environmentId
                        createdAt
                        updatedAt
                    }
                }
                pageInfo {
                    endCursor
                    hasNextPage
                }
            }
        }
        """;

        Map<String, Object> input = Map.of(
                "projectId", projectId,
                "environmentId", environmentId,
                "serviceId", serviceId,
                "includeDeleted", false
        );

        JsonNode latestDeployment = null;
        java.time.Instant latestCreatedAt = null;

        String cursor = null;
        java.util.Set<String> visitedCursors =
                new java.util.HashSet<>();

        int pageCount = 0;
        final int maxPages = 100;

        do {
            if (++pageCount > maxPages) {
                throw new DeploymentException(
                        "Railway deployment pagination exceeded 100 pages."
                );
            }

            Map<String, Object> variables =
                    new java.util.HashMap<>();

            variables.put("input", input);
            variables.put("first", 50);

            if (cursor != null) {
                variables.put("after", cursor);
            }

            JsonNode data = execute(query, variables);

            JsonNode connection = data.path("deployments");
            JsonNode edges = connection.path("edges");

            if (!edges.isArray()) {
                throw new DeploymentException(
                        "Invalid Railway deployments response."
                );
            }

            for (JsonNode edge : edges) {

                JsonNode deployment = edge.path("node");

                if (!serviceId.equals(
                        deployment.path("serviceId").asText())
                        || !environmentId.equals(
                        deployment.path("environmentId").asText())) {
                    continue;
                }

                String createdAt =
                        deployment.path("createdAt").asText();

                if (createdAt.isBlank()) {
                    continue;
                }

                java.time.Instant timestamp;

                try {
                    timestamp = java.time.Instant.parse(createdAt);
                } catch (java.time.format.DateTimeParseException e) {
                    throw new DeploymentException(
                            "Invalid Railway deployment timestamp: "
                                    + createdAt
                    );
                }

                if (latestCreatedAt == null
                        || timestamp.isAfter(latestCreatedAt)) {

                    latestCreatedAt = timestamp;
                    latestDeployment = deployment;
                }
            }

            JsonNode pageInfo = connection.path("pageInfo");

            if (!pageInfo.path("hasNextPage").asBoolean(false)) {
                break;
            }

            String nextCursor =
                    pageInfo.path("endCursor").asText();

            if (nextCursor.isBlank()
                    || !visitedCursors.add(nextCursor)) {
                throw new DeploymentException(
                        "Invalid Railway deployment pagination cursor."
                );
            }

            cursor = nextCursor;

        } while (true);

        if (latestDeployment == null) {
            throw new DeploymentException(
                    "No deployments found for Railway service: "
                            + serviceId
            );
        }

        return latestDeployment;
    }

    public String classifyDeploymentStatus(String status) {

        if (status == null || status.isBlank()) {
            throw new DeploymentException(
                    "Railway deployment status is missing."
            );
        }

        return switch (status) {

            case "SUCCESS" -> "SUCCESS";

            case "QUEUED",
                    "INITIALIZING",
                    "BUILDING",
                    "DEPLOYING",
                    "WAITING",
                    "REMOVING" -> "IN_PROGRESS";

            case "FAILED",
                    "CRASHED",
                    "REMOVED",
                    "SKIPPED" -> "FAILED";

            case "NEEDS_APPROVAL" -> "NEEDS_APPROVAL";

            case "SLEEPING" -> "SLEEPING";

            default -> throw new DeploymentException(
                    "Unknown Railway deployment status: " + status
            );
        };
    }

    public JsonNode waitForDeployment(
            String projectId,
            String environmentId,
            String serviceId
    ) {

        long timeoutMillis = 5 * 60 * 1000L;
        long pollIntervalMillis = 5000L;

        long deadline = System.nanoTime()
                + java.util.concurrent.TimeUnit.MILLISECONDS
                .toNanos(timeoutMillis);

        while (System.nanoTime() < deadline) {

            JsonNode deployment = getLatestDeployment(
                    projectId,
                    environmentId,
                    serviceId
            );

            String status = deployment.path("status").asText();

            String classification = classifyDeploymentStatus(status);

            switch (classification) {

                case "SUCCESS" -> {
                    return deployment;
                }

                case "FAILED" -> throw new DeploymentException(
                        "Railway deployment failed with status: " + status
                );

                case "NEEDS_APPROVAL" -> throw new DeploymentException(
                        "Railway deployment requires manual approval."
                );

                case "SLEEPING" -> throw new DeploymentException(
                        "Railway deployment is sleeping."
                );

                case "IN_PROGRESS" -> {
                    if ("REMOVING".equals(status)) {
                        throw new DeploymentException(
                                "Railway deployment is being removed."
                        );
                    }
                }

                default -> throw new DeploymentException(
                        "Unexpected Railway deployment classification: "
                                + classification
                );
            }

            try {
                Thread.sleep(pollIntervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                throw new DeploymentException(
                        "Interrupted while waiting for Railway deployment."
                );
            }
        }

        throw new DeploymentException(
                "Railway deployment timed out after 5 minutes."
        );
    }

    public JsonNode waitForDeploymentById(String deploymentId) {

        if (deploymentId == null || deploymentId.isBlank()) {
            throw new DeploymentException(
                    "Railway deployment ID is required."
            );
        }

        long timeoutMillis = 5 * 60 * 1000L;
        long pollIntervalMillis = 5000L;

        long deadline = System.nanoTime()
                + java.util.concurrent.TimeUnit.MILLISECONDS
                .toNanos(timeoutMillis);

        while (System.nanoTime() < deadline) {

            JsonNode deployment = getDeploymentStatus(deploymentId);

            String status = deployment.path("status").asText();

            String classification = classifyDeploymentStatus(status);

            switch (classification) {

                case "SUCCESS" -> {
                    return deployment;
                }

                case "FAILED" -> throw new DeploymentException(
                        "Railway deployment " + deploymentId
                                + " failed with status: " + status
                );

                case "NEEDS_APPROVAL" -> throw new DeploymentException(
                        "Railway deployment requires manual approval: "
                                + deploymentId
                );

                case "SLEEPING" -> throw new DeploymentException(
                        "Railway deployment is sleeping: " + deploymentId
                );

                case "IN_PROGRESS" -> {
                    if ("REMOVING".equals(status)) {
                        throw new DeploymentException(
                                "Railway deployment is being removed: "
                                        + deploymentId
                        );
                    }
                }

                default -> throw new DeploymentException(
                        "Unexpected deployment classification: "
                                + classification
                );
            }

            try {
                Thread.sleep(pollIntervalMillis);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                throw new DeploymentException(
                        "Interrupted while waiting for Railway deployment."
                );
            }
        }

        throw new DeploymentException(
                "Railway deployment timed out after 5 minutes: "
                        + deploymentId
        );
    }
}