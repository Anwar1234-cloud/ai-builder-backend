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
}