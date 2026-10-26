package com.aibuilder.deployment.service;

import com.aibuilder.deployment.dto.DeploymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FrontendDeploymentTransactionService {

    private final DeploymentService deploymentService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeploymentResponse markDeploying(Long deploymentId) {
        return deploymentService.markFrontendDeploying(deploymentId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeploymentResponse markDeployed(
            Long deploymentId,
            String vercelDeploymentId,
            String frontendUrl
    ) {
        return deploymentService.markFrontendDeployed(
                deploymentId,
                vercelDeploymentId,
                frontendUrl
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeploymentResponse markFailed(
            Long deploymentId,
            String errorMessage
    ) {
        return deploymentService.markFrontendFailed(
                deploymentId,
                errorMessage
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeploymentResponse markReady(
            Long deploymentId,
            String vercelDeploymentId,
            String frontendUrl
    ) {
        return deploymentService.markFrontendReady(
                deploymentId,
                vercelDeploymentId,
                frontendUrl
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeploymentResponse finalizeFullStackDeployment(
            Long deploymentId
    ) {
        return deploymentService.finalizeFullStackDeployment(
                deploymentId
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeploymentResponse markFullStackFailed(
            Long deploymentId,
            String errorMessage
    ) {
        return deploymentService.markFullStackDeploymentFailed(
                deploymentId,
                errorMessage
        );
    }
}