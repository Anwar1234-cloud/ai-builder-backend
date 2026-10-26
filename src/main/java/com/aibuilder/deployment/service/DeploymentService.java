package com.aibuilder.deployment.service;

import com.aibuilder.build.entity.BuildRun;
import com.aibuilder.build.entity.BuildStatus;
import com.aibuilder.build.entity.VisualValidationStatus;
import com.aibuilder.build.repository.BuildRunRepository;
import com.aibuilder.deployment.dto.DeploymentResponse;
import com.aibuilder.deployment.entity.Deployment;
import com.aibuilder.deployment.entity.DeploymentProvider;
import com.aibuilder.deployment.entity.DeploymentStatus;
import com.aibuilder.deployment.exception.DeploymentException;
import com.aibuilder.deployment.repository.DeploymentRepository;
import com.aibuilder.deployment.vercel.VercelDeploymentClient;
import com.aibuilder.project.entity.Project;
import com.aibuilder.project.repository.ProjectRepository;
import com.aibuilder.deployment.vercel.VercelDeploymentClient;
import com.aibuilder.deployment.vercel.VercelDeploymentResult;
import com.aibuilder.deployment.vercel.VercelDeploymentStatus;
import com.aibuilder.deployment.railway.RailwayCliDeploymentService;
import com.aibuilder.deployment.railway.RailwayDeploymentClient;
import org.springframework.transaction.annotation.Transactional;
import com.aibuilder.project.entity.ProjectType;

import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeploymentService {

    private final DeploymentRepository deploymentRepository;
    private final ProjectRepository projectRepository;
    private final BuildRunRepository buildRunRepository;
    private final VercelDeploymentClient vercelDeploymentClient;
    private final RailwayCliDeploymentService railwayCliDeploymentService;
    private final RailwayDeploymentClient railwayDeploymentClient;

    @Transactional
    public DeploymentResponse createDeployment(
            Long projectId
    ) {

        Project project =
                projectRepository.findById(projectId)
                        .orElseThrow(() ->
                                new DeploymentException(
                                        "Project not found: "
                                                + projectId
                                )
                        );

        BuildRun buildRun =
                buildRunRepository
                        .findFirstByProjectIdAndStatusAndVisualValidationStatusOrderByStartedAtDesc(
                                projectId,
                                BuildStatus.SUCCESS,
                                VisualValidationStatus.PASSED
                        )
                        .orElseThrow(() ->
                                new DeploymentException(
                                        "No deployable build found. "
                                                + "The project must have a successful build "
                                                + "that passed visual validation."
                                )
                        );

        boolean deploymentExists =
                deploymentRepository
                        .existsByBuildRunIdAndStatusIn(
                                buildRun.getId(),
                                List.of(
                                        DeploymentStatus.PENDING,
                                        DeploymentStatus.DEPLOYING,
                                        DeploymentStatus.DEPLOYED
                                )
                        );

        if (deploymentExists) {

            throw new DeploymentException(
                    "Build "
                            + buildRun.getId()
                            + " already has an active or "
                            + "completed deployment."
            );
        }

        Deployment deployment =
                Deployment.builder()
                        .project(project)
                        .buildRun(buildRun)
                        .provider(
                                DeploymentProvider.VERCEL
                        )
                        .status(
                                DeploymentStatus.PENDING
                        )
                        .build();

        deployment =
                deploymentRepository.save(deployment);

        Long deploymentId =
                deployment.getId();

        try {

            deployment.setStatus(
                    DeploymentStatus.DEPLOYING
            );

            deployment.setStartedAt(
                    LocalDateTime.now()
            );

            deployment =
                    deploymentRepository.save(
                            deployment
                    );

            Path workspace =
                    Path.of(
                            buildRun.getWorkspacePath()
                    );

            Path distDirectory =
                    workspace.resolve("dist");

            VercelDeploymentResult result =
                    vercelDeploymentClient.deploy(
                            distDirectory,
                            "ai-builder-"
                                    + project.getId()
                                    + "-"
                                    + project.getName()
                    );

            /*
             * Vercel accepting the deployment request does NOT mean
             * the deployment has successfully completed.
             *
             * Save the provider information first while the deployment
             * remains DEPLOYING.
             */
            deployment.setProviderDeploymentId(
                    result.deploymentId()
            );

            deployment.setDeploymentUrl(
                    result.deploymentUrl()
            );

            deployment.setStatus(
                    DeploymentStatus.DEPLOYING
            );

            deployment.setErrorMessage(null);

            deployment =
                    deploymentRepository.save(
                            deployment
                    );


            /*
             * Wait until Vercel reports READY or ERROR.
             */
            VercelDeploymentStatus vercelStatus =
                    vercelDeploymentClient.waitForDeployment(
                            result.deploymentId()
                    );


            if (!vercelStatus.isReady()) {

                throw new DeploymentException(
                        "Vercel deployment did not reach READY state."
                );
            }



            if (vercelStatus.url() != null
                    && !vercelStatus.url().isBlank()) {

                deployment.setDeploymentUrl(
                        vercelStatus.url()
                );
            }


            deployment.setStatus(
                    DeploymentStatus.DEPLOYED
            );

            deployment.setCompletedAt(
                    LocalDateTime.now()
            );

            deployment.setErrorMessage(null);

            deployment =
                    deploymentRepository.save(
                            deployment
                    );

            return DeploymentResponse.from(
                    deployment
            );

        } catch (Exception e) {

            Deployment failedDeployment =
                    deploymentRepository
                            .findById(deploymentId)
                            .orElseThrow(() ->
                                    new DeploymentException(
                                            "Deployment record disappeared: "
                                                    + deploymentId
                                    )
                            );

            failedDeployment.setStatus(
                    DeploymentStatus.FAILED
            );

            failedDeployment.setErrorMessage(
                    e.getMessage()
            );

            failedDeployment.setCompletedAt(
                    LocalDateTime.now()
            );

            deploymentRepository.save(
                    failedDeployment
            );

            throw new DeploymentException(
                    "Vercel deployment failed: "
                            + (
                            e.getMessage() != null
                                    ? e.getMessage()
                                    : e.getClass().getName()
                    ),
                    e
            );
        }
    }

    @Transactional
    public DeploymentResponse createPendingFullStackDeployment(
            Long projectId
    ) {

        // 1. Validate project
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() ->
                        new DeploymentException(
                                "Project not found: " + projectId
                        )
                );

        // 2. Allow only full-stack applications
        if (project.getType() != ProjectType.FULL_STACK_APP) {
            throw new DeploymentException(
                    "Full-stack deployment is only supported "
                            + "for FULL_STACK_APP projects."
            );
        }

        // 3. Find latest deployable build
        BuildRun buildRun = buildRunRepository
                .findFirstByProjectIdAndStatusAndVisualValidationStatusOrderByStartedAtDesc(
                        projectId,
                        BuildStatus.SUCCESS,
                        VisualValidationStatus.PASSED
                )
                .orElseThrow(() ->
                        new DeploymentException(
                                "No deployable full-stack build found. "
                                        + "Build must succeed and pass "
                                        + "visual validation."
                        )
                );

        // 4. Prevent duplicate active/completed deployments
        boolean deploymentExists = deploymentRepository
                .existsByBuildRunIdAndStatusIn(
                        buildRun.getId(),
                        List.of(
                                DeploymentStatus.PENDING,
                                DeploymentStatus.DEPLOYING,
                                DeploymentStatus.DEPLOYED
                        )
                );

        if (deploymentExists) {
            throw new DeploymentException(
                    "Build " + buildRun.getId()
                            + " already has an active or "
                            + "completed deployment."
            );
        }

        // 5. Create deployment record only
        Deployment deployment = Deployment.builder()
                .project(project)
                .buildRun(buildRun)
                .provider(DeploymentProvider.VERCEL)
                .status(DeploymentStatus.PENDING)
                .frontendStatus(DeploymentStatus.PENDING)
                .backendProvider(DeploymentProvider.RAILWAY)
                .backendStatus(DeploymentStatus.PENDING)
                .build();

        deployment = deploymentRepository.save(deployment);

        return DeploymentResponse.from(deployment);
    }


    private Path resolveBackendDirectory(BuildRun buildRun) {

        if (buildRun == null
                || buildRun.getWorkspacePath() == null
                || buildRun.getWorkspacePath().isBlank()) {

            throw new DeploymentException(
                    "Build workspace path is missing."
            );
        }

        Path workspace = Path.of(
                buildRun.getWorkspacePath()
        ).toAbsolutePath().normalize();

        Path backendDirectory = workspace.resolve(
                "backend"
        ).normalize();

        if (!java.nio.file.Files.isDirectory(backendDirectory)) {

            throw new DeploymentException(
                    "Generated backend directory not found for build "
                            + buildRun.getId()
            );
        }

        if (!java.nio.file.Files.isRegularFile(
                backendDirectory.resolve("pom.xml")
        )) {

            throw new DeploymentException(
                    "Generated backend pom.xml not found for build "
                            + buildRun.getId()
            );
        }

        return backendDirectory;
    }


    public String uploadBackendToRailway(
            Long buildRunId,
            String railwayProjectId,
            String railwayServiceId,
            String railwayEnvironmentId
    ) {

        if (buildRunId == null) {
            throw new DeploymentException(
                    "Build ID is required."
            );
        }

        BuildRun buildRun = buildRunRepository
                .findById(buildRunId)
                .orElseThrow(() -> new DeploymentException(
                        "Build not found: " + buildRunId
                ));

        if (buildRun.getStatus() != BuildStatus.SUCCESS) {
            throw new DeploymentException(
                    "Backend upload requires a successful build."
            );
        }

        if (buildRun.getVisualValidationStatus()
                != VisualValidationStatus.PASSED) {
            throw new DeploymentException(
                    "Backend upload requires passed visual validation."
            );
        }

        Path backendDirectory =
                resolveBackendDirectory(buildRun);


        return railwayCliDeploymentService.deployBackendAndTrack(
                backendDirectory,
                railwayProjectId,
                railwayServiceId,
                railwayEnvironmentId,
                railwayDeploymentClient
        );

    }


    @Transactional
    public DeploymentResponse markBackendDeploying(
            Long deploymentId,
            String railwayServiceId
    ) {

        if (railwayServiceId == null
                || railwayServiceId.isBlank()) {
            throw new DeploymentException(
                    "Railway service ID is required."
            );
        }

        Deployment deployment =
                getDeploymentEntity(deploymentId);

        if (deployment.getBackendStatus()
                == DeploymentStatus.DEPLOYING
                || deployment.getBackendStatus()
                == DeploymentStatus.DEPLOYED) {
            throw new DeploymentException(
                    "Backend deployment is already active or completed."
            );
        }

        deployment.setBackendProvider(
                DeploymentProvider.RAILWAY
        );

        deployment.setBackendServiceId(
                railwayServiceId
        );

        deployment.setBackendDeploymentId(null);
        deployment.setBackendUrl(null);

        deployment.setBackendStatus(
                DeploymentStatus.DEPLOYING
        );

        deployment.setStatus(
                DeploymentStatus.DEPLOYING
        );

        deployment.setStartedAt(
                LocalDateTime.now()
        );

        deployment.setErrorMessage(null);

        return DeploymentResponse.from(
                deploymentRepository.save(deployment)
        );
    }


    @Transactional
    public DeploymentResponse markBackendDeployed(
            Long deploymentId,
            String railwayDeploymentId,
            String backendUrl
    ) {

        if (railwayDeploymentId == null
                || railwayDeploymentId.isBlank()) {
            throw new DeploymentException(
                    "Railway deployment ID is required."
            );
        }

        Deployment deployment =
                getDeploymentEntity(deploymentId);

        if (deployment.getBackendProvider()
                != DeploymentProvider.RAILWAY) {
            throw new DeploymentException(
                    "Deployment is not configured for Railway backend."
            );
        }

        if (deployment.getBackendStatus()
                != DeploymentStatus.DEPLOYING) {
            throw new DeploymentException(
                    "Backend must be DEPLOYING before it can be DEPLOYED."
            );
        }

        deployment.setBackendDeploymentId(
                railwayDeploymentId
        );

        deployment.setBackendUrl(
                backendUrl
        );

        deployment.setBackendStatus(
                DeploymentStatus.DEPLOYED
        );

        deployment.setErrorMessage(null);

        return DeploymentResponse.from(
                deploymentRepository.save(deployment)
        );
    }


    @Transactional
    public DeploymentResponse markBackendFailed(
            Long deploymentId,
            String errorMessage
    ) {

        Deployment deployment =
                getDeploymentEntity(deploymentId);

        if (deployment.getBackendProvider()
                != DeploymentProvider.RAILWAY) {
            throw new DeploymentException(
                    "Deployment is not configured for Railway backend."
            );
        }

        if (deployment.getBackendStatus()
                == DeploymentStatus.DEPLOYED) {
            throw new DeploymentException(
                    "Cannot mark a deployed backend as failed."
            );
        }

        if (deployment.getBackendStatus()
                != DeploymentStatus.DEPLOYING) {
            throw new DeploymentException(
                    "Backend must be DEPLOYING before it can be FAILED."
            );
        }

        deployment.setBackendStatus(
                DeploymentStatus.FAILED
        );

        deployment.setStatus(
                DeploymentStatus.FAILED
        );

        deployment.setErrorMessage(
                errorMessage == null || errorMessage.isBlank()
                        ? "Railway backend deployment failed."
                        : errorMessage
        );

        deployment.setCompletedAt(
                LocalDateTime.now()
        );

        return DeploymentResponse.from(
                deploymentRepository.save(deployment)
        );
    }

    @Transactional
    public DeploymentResponse markBackendProvisioningFailed(
            Long deploymentId,
            String errorMessage
    ) {

        Deployment deployment = getDeploymentEntity(deploymentId);

        if (deployment.getBackendProvider() != DeploymentProvider.RAILWAY) {
            throw new DeploymentException(
                    "Backend provider must be RAILWAY."
            );
        }

        if (deployment.getBackendStatus() != DeploymentStatus.PENDING) {
            throw new DeploymentException(
                    "Only a PENDING backend can be marked as provisioning failed."
            );
        }

        deployment.setBackendStatus(DeploymentStatus.FAILED);
        deployment.setStatus(DeploymentStatus.FAILED);

        deployment.setErrorMessage(
                errorMessage == null || errorMessage.isBlank()
                        ? "Railway backend provisioning failed."
                        : errorMessage
        );

        deployment.setCompletedAt(LocalDateTime.now());

        return DeploymentResponse.from(
                deploymentRepository.save(deployment)
        );
    }


    @Transactional
    public DeploymentResponse recordBackendDeploymentId(
            Long deploymentId,
            String railwayDeploymentId
    ) {
        if (railwayDeploymentId == null
                || railwayDeploymentId.isBlank()) {
            throw new DeploymentException(
                    "Railway deployment ID is required."
            );
        }

        Deployment deployment = deploymentRepository
                .findById(deploymentId)
                .orElseThrow(() -> new DeploymentException(
                        "Deployment not found: " + deploymentId
                ));

        if (deployment.getBackendProvider()
                != DeploymentProvider.RAILWAY) {
            throw new DeploymentException(
                    "Backend provider must be RAILWAY."
            );
        }

        if (deployment.getBackendStatus()
                != DeploymentStatus.DEPLOYING) {
            throw new DeploymentException(
                    "Backend must be DEPLOYING."
            );
        }

        deployment.setBackendDeploymentId(railwayDeploymentId);

        return DeploymentResponse.from(
                deploymentRepository.save(deployment)
        );
    }

    @Transactional
    public DeploymentResponse markFrontendDeploying(Long deploymentId) {

        Deployment deployment = getDeploymentEntity(deploymentId);

        if (deployment.getBackendStatus() != DeploymentStatus.DEPLOYED) {
            throw new DeploymentException(
                    "Backend must be DEPLOYED before frontend deployment."
            );
        }

        if (deployment.getBackendUrl() == null
                || deployment.getBackendUrl().isBlank()) {
            throw new DeploymentException(
                    "Railway backend URL is required."
            );
        }

        if (deployment.getFrontendStatus() != DeploymentStatus.PENDING) {
            throw new DeploymentException(
                    "Frontend must be PENDING before deployment."
            );
        }

        deployment.setFrontendStatus(DeploymentStatus.DEPLOYING);
        deployment.setStatus(DeploymentStatus.DEPLOYING);
        deployment.setErrorMessage(null);

        return DeploymentResponse.from(
                deploymentRepository.save(deployment)
        );
    }


    @Transactional
    public DeploymentResponse markFrontendDeployed(
            Long deploymentId,
            String vercelDeploymentId,
            String frontendUrl
    ) {

        if (vercelDeploymentId == null || vercelDeploymentId.isBlank()
                || frontendUrl == null || frontendUrl.isBlank()) {
            throw new DeploymentException(
                    "Vercel deployment ID and frontend URL are required."
            );
        }

        Deployment deployment = getDeploymentEntity(deploymentId);

        if (deployment.getBackendStatus() != DeploymentStatus.DEPLOYED
                || deployment.getFrontendStatus() != DeploymentStatus.DEPLOYING) {
            throw new DeploymentException(
                    "Backend must be DEPLOYED and frontend must be DEPLOYING."
            );
        }

        deployment.setProviderDeploymentId(vercelDeploymentId);
        deployment.setDeploymentUrl(frontendUrl);

        deployment.setFrontendStatus(DeploymentStatus.DEPLOYED);
        deployment.setStatus(DeploymentStatus.DEPLOYED);
        deployment.setCompletedAt(LocalDateTime.now());
        deployment.setErrorMessage(null);

        return DeploymentResponse.from(
                deploymentRepository.save(deployment)
        );
    }


    @Transactional
    public DeploymentResponse markFrontendFailed(
            Long deploymentId,
            String errorMessage
    ) {

        Deployment deployment = getDeploymentEntity(deploymentId);

        if (deployment.getFrontendStatus() != DeploymentStatus.DEPLOYING) {
            throw new DeploymentException(
                    "Frontend must be DEPLOYING before it can be FAILED."
            );
        }

        deployment.setFrontendStatus(DeploymentStatus.FAILED);
        deployment.setStatus(DeploymentStatus.FAILED);

        deployment.setErrorMessage(
                errorMessage == null || errorMessage.isBlank()
                        ? "Frontend deployment failed."
                        : errorMessage
        );

        deployment.setCompletedAt(LocalDateTime.now());

        return DeploymentResponse.from(
                deploymentRepository.save(deployment)
        );
    }








    @Transactional(readOnly = true)
    public List<DeploymentResponse> getDeployments(
            Long projectId
    ) {

        projectRepository.findById(projectId)
                .orElseThrow(() ->
                        new DeploymentException(
                                "Project not found: " + projectId
                        )
                );

        return deploymentRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(DeploymentResponse::from)
                .toList();
    }


    @Transactional(readOnly = true)
    public DeploymentResponse getLatestDeployment(
            Long projectId
    ) {

        projectRepository.findById(projectId)
                .orElseThrow(() ->
                        new DeploymentException(
                                "Project not found: " + projectId
                        )
                );

        Deployment deployment =
                deploymentRepository
                        .findFirstByProjectIdOrderByCreatedAtDesc(
                                projectId
                        )
                        .orElseThrow(() ->
                                new DeploymentException(
                                        "No deployment found for project: "
                                                + projectId
                                )
                        );

        return DeploymentResponse.from(deployment);
    }


    @Transactional
    public Deployment markDeploying(Long deploymentId) {

        Deployment deployment =
                getDeploymentEntity(deploymentId);

        deployment.setStatus(
                DeploymentStatus.DEPLOYING
        );

        deployment.setStartedAt(
                LocalDateTime.now()
        );

        deployment.setErrorMessage(null);

        return deploymentRepository.save(
                deployment
        );
    }


    @Transactional
    public Deployment markDeployed(
            Long deploymentId,
            String providerDeploymentId,
            String deploymentUrl
    ) {

        Deployment deployment =
                getDeploymentEntity(deploymentId);

        deployment.setStatus(
                DeploymentStatus.DEPLOYED
        );

        deployment.setProviderDeploymentId(
                providerDeploymentId
        );

        deployment.setDeploymentUrl(
                deploymentUrl
        );

        deployment.setCompletedAt(
                LocalDateTime.now()
        );

        deployment.setErrorMessage(null);

        return deploymentRepository.save(
                deployment
        );
    }


    @Transactional
    public Deployment markFailed(
            Long deploymentId,
            String errorMessage
    ) {

        Deployment deployment =
                getDeploymentEntity(deploymentId);

        deployment.setStatus(
                DeploymentStatus.FAILED
        );

        deployment.setErrorMessage(
                errorMessage
        );

        deployment.setCompletedAt(
                LocalDateTime.now()
        );

        return deploymentRepository.save(
                deployment
        );
    }


    @Transactional(readOnly = true)
    public Deployment getDeploymentEntity(
            Long deploymentId
    ) {

        return deploymentRepository
                .findById(deploymentId)
                .orElseThrow(() ->
                        new DeploymentException(
                                "Deployment not found: "
                                        + deploymentId
                        )
                );
    }

    @Transactional
    public DeploymentResponse markFrontendReady(
            Long deploymentId,
            String vercelDeploymentId,
            String frontendUrl
    ) {

        Deployment deployment = getDeploymentEntity(deploymentId);

        if (deployment.getBackendStatus() != DeploymentStatus.DEPLOYED) {
            throw new DeploymentException(
                    "Backend must be deployed before frontend is ready."
            );
        }

        if (deployment.getFrontendStatus() != DeploymentStatus.DEPLOYING) {
            throw new DeploymentException(
                    "Frontend must be deploying before it can be ready."
            );
        }

        if (vercelDeploymentId == null || vercelDeploymentId.isBlank()
                || frontendUrl == null || frontendUrl.isBlank()) {
            throw new DeploymentException(
                    "Vercel deployment ID and frontend URL are required."
            );
        }

        deployment.setProviderDeploymentId(vercelDeploymentId);
        deployment.setDeploymentUrl(frontendUrl);

        deployment.setFrontendStatus(DeploymentStatus.DEPLOYED);

        // Railway CORS configuration is not verified yet.
        deployment.setStatus(DeploymentStatus.DEPLOYING);
        deployment.setCompletedAt(null);

        return DeploymentResponse.from(
                deploymentRepository.save(deployment)
        );
    }

    @Transactional
    public DeploymentResponse finalizeFullStackDeployment(
            Long deploymentId
    ) {
        Deployment deployment = getDeploymentEntity(deploymentId);

        if (deployment.getStatus() != DeploymentStatus.DEPLOYING) {
            throw new DeploymentException(
                    "Deployment must be DEPLOYING before finalization."
            );
        }

        if (deployment.getBackendStatus() != DeploymentStatus.DEPLOYED
                || deployment.getFrontendStatus() != DeploymentStatus.DEPLOYED) {
            throw new DeploymentException(
                    "Backend and frontend must both be DEPLOYED."
            );
        }

        if (deployment.getBackendUrl() == null
                || deployment.getBackendUrl().isBlank()
                || deployment.getDeploymentUrl() == null
                || deployment.getDeploymentUrl().isBlank()) {
            throw new DeploymentException(
                    "Backend and frontend URLs are required."
            );
        }

        deployment.setStatus(DeploymentStatus.DEPLOYED);
        deployment.setCompletedAt(java.time.LocalDateTime.now());
        deployment.setErrorMessage(null);

        return DeploymentResponse.from(
                deploymentRepository.save(deployment)
        );
    }

    @Transactional
    public DeploymentResponse markFullStackDeploymentFailed(
            Long deploymentId,
            String errorMessage
    ) {

        Deployment deployment = getDeploymentEntity(deploymentId);

        if (deployment.getStatus() != DeploymentStatus.DEPLOYING) {
            throw new DeploymentException(
                    "Only an active deployment can be marked as failed."
            );
        }

        String message = errorMessage == null || errorMessage.isBlank()
                ? "Full-stack deployment finalization failed."
                : errorMessage;

        deployment.setStatus(DeploymentStatus.FAILED);
        deployment.setErrorMessage(message);
        deployment.setCompletedAt(LocalDateTime.now());

        return DeploymentResponse.from(
                deploymentRepository.save(deployment)
        );
    }
}