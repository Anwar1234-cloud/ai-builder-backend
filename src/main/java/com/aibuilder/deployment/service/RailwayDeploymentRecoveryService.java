
package com.aibuilder.deployment.service;

import com.aibuilder.deployment.dto.DeploymentResponse;
import com.aibuilder.deployment.entity.Deployment;
import com.aibuilder.deployment.entity.DeploymentProvider;
import com.aibuilder.deployment.entity.DeploymentStatus;
import com.aibuilder.deployment.exception.DeploymentException;
import com.aibuilder.deployment.railway.RailwayDeploymentClient;
import com.aibuilder.deployment.repository.RailwayProjectResourcesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class RailwayDeploymentRecoveryService {

    private final DeploymentService deploymentService;

    private final BackendDeploymentTransactionService transactionService;

    private final RailwayDeploymentClient railwayClient;

    private final RailwayBackendUrlService backendUrlService;

    private final RailwayProjectResourcesRepository resourcesRepository;

    public DeploymentResponse recoverDeployment(Long deploymentId) {

        if (deploymentId == null) {
            throw new DeploymentException(
                    "Deployment ID is required."
            );
        }

        Deployment deployment =
                deploymentService.getDeploymentEntity(deploymentId);

        if (deployment.getBackendProvider()
                != DeploymentProvider.RAILWAY) {
            throw new DeploymentException(
                    "Backend provider must be RAILWAY."
            );
        }

        if (deployment.getBackendStatus()
                != DeploymentStatus.DEPLOYING) {
            throw new DeploymentException(
                    "Only DEPLOYING backends can be recovered."
            );
        }

        String railwayDeploymentId =
                deployment.getBackendDeploymentId();

        if (railwayDeploymentId == null
                || railwayDeploymentId.isBlank()) {
            throw new DeploymentException(
                    "Railway deployment ID is missing."
            );
        }

        Long projectId = deployment.getProject().getId();

        var resources = resourcesRepository
                .findByProjectId(projectId)
                .orElseThrow(() -> new DeploymentException(
                        "Railway resources not found for project: "
                                + projectId
                ));

        String serviceId = resources.getRailwayBackendServiceId();
        String environmentId = resources.getRailwayEnvironmentId();

        if (!serviceId.equals(deployment.getBackendServiceId())) {
            throw new DeploymentException(
                    "Railway backend service ID mismatch."
            );
        }

        JsonNode railwayDeployment =
                railwayClient.getDeploymentStatus(
                        railwayDeploymentId
                );

        if (!railwayDeploymentId.equals(
                railwayDeployment.path("id").asText()
        )) {
            throw new DeploymentException(
                    "Railway deployment ID mismatch."
            );
        }

        String status =
                railwayDeployment.path("status").asText();

        if (!"SUCCESS".equals(status)) {
            throw new DeploymentException(
                    "Railway deployment is not successful. Status: "
                            + status
            );
        }

        String backendUrl =
                backendUrlService.resolveBackendUrl(
                        resources.getRailwayProjectId(),
                        environmentId,
                        serviceId
                );

        return transactionService.markDeployed(
                deploymentId,
                railwayDeploymentId,
                backendUrl
        );
    }
}
