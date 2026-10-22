
package com.aibuilder.deployment.railway;

import com.aibuilder.deployment.exception.DeploymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RailwayProvisioningService {

    private final RailwayDeploymentClient railwayClient;

    public RailwayResources prepareBackend(
            String projectName,
            String workspaceId
    ) {

        if (projectName == null || projectName.isBlank()) {
            throw new DeploymentException(
                    "Railway project name is required."
            );
        }

        if (workspaceId == null || workspaceId.isBlank()) {
            throw new DeploymentException(
                    "Railway workspace ID is required."
            );
        }

        String projectId = railwayClient.createProject(
                projectName,
                workspaceId
        );

        if (projectId.isBlank()) {
            throw new DeploymentException(
                    "Railway project creation returned no ID."
            );
        }

        String environmentId =
                railwayClient.getEnvironmentId(projectId);

        if (environmentId == null
                || environmentId.isBlank()) {
            throw new DeploymentException(
                    "Railway environment ID is missing."
            );
        }

        String serviceId = railwayClient.createService(
                projectId,
                "backend"
        );

        if (serviceId.isBlank()) {
            throw new DeploymentException(
                    "Railway backend service ID is missing."
            );
        }

        return new RailwayResources(
                projectId,
                environmentId,
                serviceId
        );
    }

    public record RailwayResources(
            String projectId,
            String environmentId,
            String serviceId
    ) {}



    public String provisionPostgres(
            RailwayResources resources
    ) {
        if (resources == null) {
            throw new DeploymentException(
                    "Railway resources are required."
            );
        }

        railwayClient.deployPostgres(
                resources.projectId(),
                resources.environmentId()
        );

        long deadline = System.nanoTime()
                + java.util.concurrent.TimeUnit.MINUTES.toNanos(3);

        while (System.nanoTime() < deadline) {
            try {
                String postgresServiceId =
                        railwayClient.getPostgresServiceId(
                                resources.projectId()
                        );

                if (postgresServiceId != null
                        && !postgresServiceId.isBlank()) {
                    return postgresServiceId;
                }

            } catch (DeploymentException e) {
                // Retry only while the template-created service
                // has not appeared yet.
                if (e.getMessage() == null
                        || !e.getMessage().contains(
                        "PostgreSQL service not found"
                )) {
                    throw e;
                }
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DeploymentException(
                        "Interrupted while waiting for PostgreSQL.",
                        e
                );
            }
        }

        throw new DeploymentException(
                "Timed out waiting for Railway PostgreSQL service."
        );
    }



    public void configureBackendDatabase(
            RailwayResources resources
    ) {

        if (resources == null) {
            throw new DeploymentException(
                    "Railway resources are required."
            );
        }

        String projectId = resources.projectId();
        String environmentId = resources.environmentId();
        String backendServiceId = resources.serviceId();

        railwayClient.upsertVariable(
                projectId,
                environmentId,
                backendServiceId,
                "SPRING_DATASOURCE_URL",
                "jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}"
        );

        railwayClient.upsertVariable(
                projectId,
                environmentId,
                backendServiceId,
                "SPRING_DATASOURCE_USERNAME",
                "${{Postgres.PGUSER}}"
        );

        railwayClient.upsertVariable(
                projectId,
                environmentId,
                backendServiceId,
                "SPRING_DATASOURCE_PASSWORD",
                "${{Postgres.PGPASSWORD}}"
        );

        railwayClient.upsertVariable(
                projectId,
                environmentId,
                backendServiceId,
                "SERVER_PORT",
                "${{PORT}}"
        );
    }


}
