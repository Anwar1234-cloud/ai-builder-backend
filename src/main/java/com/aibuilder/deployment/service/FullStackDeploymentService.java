
package com.aibuilder.deployment.service;

import com.aibuilder.build.entity.BuildRun;
import com.aibuilder.build.repository.BuildRunRepository;
import com.aibuilder.build.service.BuildExecutorService;
import com.aibuilder.build.service.BuildExecutorService.ExecutionResult;
import com.aibuilder.deployment.dto.DeploymentResponse;
import com.aibuilder.deployment.entity.Deployment;
import com.aibuilder.deployment.entity.DeploymentStatus;
import com.aibuilder.deployment.entity.RailwayProjectResources;
import com.aibuilder.deployment.exception.DeploymentException;
import com.aibuilder.deployment.railway.RailwayProvisioningService;
import com.aibuilder.deployment.repository.RailwayProjectResourcesRepository;
import com.aibuilder.deployment.vercel.VercelDeploymentClient;
import com.aibuilder.deployment.vercel.VercelDeploymentResult;
import com.aibuilder.deployment.vercel.VercelDeploymentStatus;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FullStackDeploymentService {

    private final DeploymentService deploymentService;

    private final RailwayAutomaticDeploymentService
            railwayAutomaticDeploymentService;

    private final FrontendDeploymentTransactionService
            frontendTransactionService;

    private final BuildExecutorService buildExecutorService;

    private final VercelDeploymentClient vercelDeploymentClient;

    private final BuildRunRepository buildRunRepository;

    private final BackendDeploymentTransactionService
            backendTransactionService;

    private final RailwayProjectResourcesRepository
            railwayResourcesRepository;

    private final RailwayProvisioningService railwayProvisioningService;

    @Value("${deployment.railway.workspace-id:}")
    private String railwayWorkspaceId;

    public DeploymentResponse deploy(Long projectId) {

        // STEP 1: Create pending deployment
        DeploymentResponse pending =
                deploymentService.createPendingFullStackDeployment(
                        projectId
                );

        Long deploymentId = pending.id();
        Long buildRunId = pending.buildId();

        // STEP 2: Deploy backend to Railway
        DeploymentResponse backendResult;

        try {

            backendResult =
                    railwayAutomaticDeploymentService.deploy(
                            deploymentId,
                            buildRunId,
                            "ai-builder-" + projectId,
                            railwayWorkspaceId,
                            null
                    );

        } catch (Exception e) {

            try {

                Deployment currentDeployment =
                        deploymentService.getDeploymentEntity(
                                deploymentId
                        );

                if (currentDeployment.getBackendStatus()
                        == DeploymentStatus.PENDING) {

                    backendTransactionService.markProvisioningFailed(
                            deploymentId,
                            e.getMessage()
                    );
                }

            } catch (Exception statusException) {

                e.addSuppressed(statusException);
            }

            throw new DeploymentException(
                    "Railway backend deployment failed: "
                            + e.getMessage(),
                    e
            );
        }

        // STEP 3: Validate Railway backend URL
        String backendUrl = backendResult.backendUrl();

        if (backendUrl == null || backendUrl.isBlank()) {

            String errorMessage =
                    "Railway backend deployed but no public backend URL was returned.";

            frontendTransactionService.markFullStackFailed(
                    deploymentId,
                    errorMessage
            );

            throw new DeploymentException(errorMessage);
        }

        // STEP 4: Mark frontend deployment as started
        frontendTransactionService.markDeploying(deploymentId);

        try {

            // STEP 5: Resolve generated frontend workspace
            BuildRun buildRun =
                    buildRunRepository.findById(buildRunId)
                            .orElseThrow(() ->
                                    new DeploymentException(
                                            "Build not found: "
                                                    + buildRunId
                                    )
                            );

            Path frontendDirectory =
                    Path.of(buildRun.getWorkspacePath())
                            .resolve("frontend");

            if (!Files.isDirectory(frontendDirectory)
                    || !Files.isRegularFile(
                    frontendDirectory.resolve("package.json")
            )) {

                throw new DeploymentException(
                        "Generated frontend workspace is missing."
                );
            }

            // STEP 6: Build frontend with Railway backend URL
            ExecutionResult buildResult =
                    buildExecutorService.execute(
                            frontendDirectory,
                            "npm run build",
                            Map.of(
                                    "VITE_API_BASE_URL",
                                    backendUrl
                            )
                    );

            if (!buildResult.isSuccess()) {

                throw new DeploymentException(
                        "Frontend production build failed: "
                                + buildResult.errorOutput()
                                + "\n"
                                + buildResult.output()
                );
            }

            Path distDirectory =
                    frontendDirectory.resolve("dist");

            if (!Files.isRegularFile(
                    distDirectory.resolve("index.html")
            )) {

                throw new DeploymentException(
                        "Frontend build completed but "
                                + "dist/index.html is missing."
                );
            }

            // STEP 7: Deploy frontend to Vercel
            VercelDeploymentResult vercelResult =
                    vercelDeploymentClient.deploy(
                            distDirectory,
                            "ai-builder-" + projectId
                    );

            // STEP 8: Wait for Vercel READY
            VercelDeploymentStatus vercelStatus =
                    vercelDeploymentClient.waitForDeployment(
                            vercelResult.deploymentId()
                    );

            if (!vercelStatus.isReady()) {

                throw new DeploymentException(
                        "Vercel deployment did not become READY: "
                                + vercelStatus.errorMessage()
                );
            }

            String frontendUrl =
                    vercelStatus.url() != null
                            && !vercelStatus.url().isBlank()
                            ? vercelStatus.url()
                            : vercelResult.deploymentUrl();

            frontendUrl = normalizeFrontendUrl(frontendUrl);

            // STEP 9: Save Vercel READY state
            frontendTransactionService.markReady(
                    deploymentId,
                    vercelResult.deploymentId(),
                    frontendUrl
            );

            // STEP 10: Resolve Railway resources
            RailwayProjectResources railwayResources =
                    railwayResourcesRepository
                            .findByProjectId(projectId)
                            .orElseThrow(() ->
                                    new DeploymentException(
                                            "Railway resources not found "
                                                    + "for project: "
                                                    + projectId
                                    )
                            );

            // STEP 11: Configure backend CORS origin
            railwayProvisioningService.configureFrontendUrl(
                    railwayResources.getRailwayProjectId(),
                    railwayResources.getRailwayEnvironmentId(),
                    railwayResources.getRailwayBackendServiceId(),
                    frontendUrl
            );

            // STEP 12: Redeploy Railway backend
            String redeployedBackendId =
                    railwayProvisioningService.redeployBackendAndWait(
                            railwayResources.getRailwayProjectId(),
                            railwayResources.getRailwayEnvironmentId(),
                            railwayResources.getRailwayBackendServiceId()
                    );

            if (redeployedBackendId == null
                    || redeployedBackendId.isBlank()) {

                throw new DeploymentException(
                        "Railway redeployment did not return "
                                + "a deployment ID."
                );
            }

            // STEP 13: Finalize full-stack deployment
            return frontendTransactionService
                    .finalizeFullStackDeployment(
                            deploymentId
                    );

        } catch (Exception e) {

            // STEP 14: Handle failures according to current state
            try {

                Deployment currentDeployment =
                        deploymentService.getDeploymentEntity(
                                deploymentId
                        );

                DeploymentStatus frontendStatus =
                        currentDeployment.getFrontendStatus();

                DeploymentStatus overallStatus =
                        currentDeployment.getStatus();

                if (overallStatus == DeploymentStatus.DEPLOYING) {

                    if (frontendStatus
                            == DeploymentStatus.DEPLOYING) {

                        // Frontend build or Vercel deployment failed.
                        frontendTransactionService.markFailed(
                                deploymentId,
                                e.getMessage()
                        );

                    } else if (frontendStatus
                            == DeploymentStatus.DEPLOYED) {

                        // Vercel succeeded, but Railway CORS
                        // configuration or redeployment failed.
                        frontendTransactionService.markFullStackFailed(
                                deploymentId,
                                e.getMessage()
                        );
                    }
                }

            } catch (Exception statusException) {

                // Preserve the original deployment exception.
                e.addSuppressed(statusException);
            }

            throw new DeploymentException(
                    "Full-stack deployment failed: "
                            + e.getMessage(),
                    e
            );
        }
    }

    private String normalizeFrontendUrl(String frontendUrl) {

        if (frontendUrl == null || frontendUrl.isBlank()) {

            throw new DeploymentException(
                    "Vercel frontend URL is missing."
            );
        }

        String normalized = frontendUrl.trim();

        if (!normalized.contains("://")) {
            normalized = "https://" + normalized;
        }

        java.net.URI uri;

        try {

            uri = java.net.URI.create(normalized);

        } catch (IllegalArgumentException e) {

            throw new DeploymentException(
                    "Invalid Vercel frontend URL.",
                    e
            );
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || (uri.getPath() != null
                && !uri.getPath().isEmpty()
                && !"/".equals(uri.getPath()))) {

            throw new DeploymentException(
                    "Vercel frontend URL must be a valid HTTPS origin."
            );
        }

        String origin = "https://" + uri.getHost();

        if (uri.getPort() != -1) {
            origin += ":" + uri.getPort();
        }

        return origin;
    }
}
