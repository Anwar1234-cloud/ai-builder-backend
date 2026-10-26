
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

    private final RailwayBackendHealthService backendHealthService;

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

        try {

            // STEP 1: Persist Railway deployment ID
            transactionService.recordDeploymentId(
                    deploymentId,
                    railwayDeploymentId
            );

            // STEP 2: Resolve public backend URL
            String resolvedBackendUrl =
                    backendUrlService.resolveBackendUrl(
                            railwayProjectId,
                            railwayEnvironmentId,
                            railwayServiceId
                    );

            if (resolvedBackendUrl == null
                    || resolvedBackendUrl.isBlank()) {

                throw new DeploymentException(
                        "Railway backend URL is missing."
                );
            }

// STEP 3: Verify actual backend HTTP health
            backendHealthService.verifyHealth(resolvedBackendUrl);

// STEP 4: Mark backend deployed only after health verification
            return transactionService.markDeployed(
                    deploymentId,
                    railwayDeploymentId,
                    resolvedBackendUrl
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
                    "Railway backend deployment finalization failed: "
                            + e.getMessage(),
                    e
            );
        }
    }
}
