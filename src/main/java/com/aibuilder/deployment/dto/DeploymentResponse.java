package com.aibuilder.deployment.dto;

import com.aibuilder.deployment.entity.Deployment;
import com.aibuilder.deployment.entity.DeploymentProvider;
import com.aibuilder.deployment.entity.DeploymentStatus;

import java.time.LocalDateTime;

public record DeploymentResponse(

        Long id,

        Long projectId,

        Long buildId,

        DeploymentProvider provider,

        DeploymentStatus status,

        String deploymentUrl,

        String errorMessage,

        LocalDateTime createdAt,

        LocalDateTime startedAt,

        LocalDateTime completedAt

) {

    public static DeploymentResponse from(
            Deployment deployment
    ) {

        return new DeploymentResponse(

                deployment.getId(),

                deployment
                        .getProject()
                        .getId(),

                deployment
                        .getBuildRun()
                        .getId(),

                deployment.getProvider(),

                deployment.getStatus(),

                deployment.getDeploymentUrl(),

                deployment.getErrorMessage(),

                deployment.getCreatedAt(),

                deployment.getStartedAt(),

                deployment.getCompletedAt()
        );
    }
}