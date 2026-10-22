
package com.aibuilder.deployment.service;

import com.aibuilder.deployment.dto.DeploymentResponse;
import com.aibuilder.deployment.exception.DeploymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RailwayBackendOrchestrationService {

    private final DeploymentService deploymentService;

    private final BackendDeploymentTransactionService transactionService;

    private final RailwayBackendUrlService backendUrlService;

    public DeploymentResponse deployBackend(
            Long deploymentId,
            Long buildRunId,
            String railwayProjectId,
            String railwayServiceId,
            String railwayEnvironmentId,
            String backendUrl
    ) {

        if (deploymentId == null || buildRunId == null) {
            throw new DeploymentException(
                    "Deployment ID and build ID are required."
            );
        }

        var deployment =
                deploymentService.getDeploymentEntity(deploymentId);

        if (!buildRunId.equals(deployment.getBuildRun().getId())) {
            throw new DeploymentException(
                    "Deployment does not belong to the requested build."
            );
        }

        transactionService.markDeploying(
                deploymentId,
                railwayServiceId
        );

        String railwayDeploymentId;

        try {
            // Upload backend and wait for Railway SUCCESS.
            railwayDeploymentId =
                    deploymentService.uploadBackendToRailway(
                            buildRunId,
                            railwayProjectId,
                            railwayServiceId,
                            railwayEnvironmentId
                    );

        } catch (Exception e) {

            try {
                transactionService.markFailed(
                        deploymentId,
                        e.getMessage()
                );
            } catch (Exception statusException) {
                e.addSuppressed(statusException);
            }

            throw new DeploymentException(
                    "Railway backend deployment failed: "
                            + e.getMessage(),
                    e
            );
        }

        // Persist deployment ID before resolving the URL.
        transactionService.recordDeploymentId(
                deploymentId,
                railwayDeploymentId
        );

        String resolvedBackendUrl;

        try {
            resolvedBackendUrl =
                    backendUrlService.resolveBackendUrl(
                            railwayProjectId,
                            railwayEnvironmentId,
                            railwayServiceId
                    );

        } catch (Exception e) {
            // Railway deployment may already be successful.
            // Keep DEPLOYING state for recovery.
            throw new DeploymentException(
                    "Railway deployment succeeded, but backend URL "
                            + "resolution failed. Deployment ID: "
                            + railwayDeploymentId,
                    e
            );
        }

        return transactionService.markDeployed(
                deploymentId,
                railwayDeploymentId,
                resolvedBackendUrl
        );
    }
}
