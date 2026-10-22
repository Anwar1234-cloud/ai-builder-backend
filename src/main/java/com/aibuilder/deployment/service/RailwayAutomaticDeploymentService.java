
package com.aibuilder.deployment.service;

import com.aibuilder.deployment.dto.DeploymentResponse;
import com.aibuilder.deployment.entity.RailwayProjectResources;
import com.aibuilder.deployment.exception.DeploymentException;
import com.aibuilder.deployment.railway.RailwayProvisioningService;
import com.aibuilder.deployment.repository.RailwayProjectResourcesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RailwayAutomaticDeploymentService {

    private final RailwayProvisioningService provisioningService;
    private final RailwayBackendOrchestrationService orchestrationService;
    private final RailwayProjectResourcesRepository resourcesRepository;
    private final RailwayResourceRegistrationService registrationService;
    private final DeploymentService deploymentService;

    public DeploymentResponse deploy(
            Long deploymentId,
            Long buildRunId,
            String projectName,
            String workspaceId,
            String backendUrl
    ) {

        if (deploymentId == null || buildRunId == null) {
            throw new DeploymentException(
                    "Deployment ID and build ID are required."
            );
        }

        var deployment = deploymentService.getDeploymentEntity(deploymentId);

        if (!buildRunId.equals(deployment.getBuildRun().getId())) {
            throw new DeploymentException(
                    "Deployment does not belong to the requested build."
            );
        }

        Long projectId = deployment.getProject().getId();

        // Reuse existing resources when available.
        if (resourcesRepository.findByProjectId(projectId).isPresent()) {
            return deployUsingExistingResources(
                    projectId,
                    deploymentId,
                    buildRunId,
                    backendUrl
            );
        }

        // Create Railway project and backend service.
        var resources = provisioningService.prepareBackend(
                projectName,
                workspaceId
        );

        // Save resource IDs immediately after creation.
        registrationService.register(
                projectId,
                resources.projectId(),
                resources.environmentId(),
                resources.serviceId(),
                null
        );

        // Provision PostgreSQL.
        String postgresServiceId =
                provisioningService.provisionPostgres(resources);

        // Save PostgreSQL service ID.
        registrationService.register(
                projectId,
                resources.projectId(),
                resources.environmentId(),
                resources.serviceId(),
                postgresServiceId
        );

        // Configure backend database variables.
        provisioningService.configureBackendDatabase(resources);

        // Deploy backend.
        return orchestrationService.deployBackend(
                deploymentId,
                buildRunId,
                resources.projectId(),
                resources.serviceId(),
                resources.environmentId(),
                backendUrl
        );
    }

    public DeploymentResponse deployUsingExistingResources(
            Long projectId,
            Long deploymentId,
            Long buildRunId,
            String backendUrl
    ) {

        if (projectId == null
                || deploymentId == null
                || buildRunId == null) {
            throw new DeploymentException(
                    "Project, deployment and build IDs are required."
            );
        }

        RailwayProjectResources resources =
                resourcesRepository.findByProjectId(projectId)
                        .orElseThrow(() -> new DeploymentException(
                                "Railway resources are not configured for project: "
                                        + projectId
                        ));

        var deployment = deploymentService.getDeploymentEntity(deploymentId);

        if (!projectId.equals(deployment.getProject().getId())) {
            throw new DeploymentException(
                    "Deployment does not belong to the requested project."
            );
        }

        if (!buildRunId.equals(deployment.getBuildRun().getId())) {
            throw new DeploymentException(
                    "Deployment does not belong to the requested build."
            );
        }

        if (resources.getRailwayPostgresServiceId() == null
                || resources.getRailwayPostgresServiceId().isBlank()) {
            throw new DeploymentException(
                    "Railway PostgreSQL provisioning is incomplete."
            );
        }

        return orchestrationService.deployBackend(
                deploymentId,
                buildRunId,
                resources.getRailwayProjectId(),
                resources.getRailwayBackendServiceId(),
                resources.getRailwayEnvironmentId(),
                backendUrl
        );
    }
}
