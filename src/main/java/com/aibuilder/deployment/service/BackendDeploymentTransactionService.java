
package com.aibuilder.deployment.service;

import com.aibuilder.deployment.dto.DeploymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BackendDeploymentTransactionService {

    private final DeploymentService deploymentService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeploymentResponse markDeploying(
            Long deploymentId,
            String railwayServiceId
    ) {
        return deploymentService.markBackendDeploying(
                deploymentId,
                railwayServiceId
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeploymentResponse markDeployed(
            Long deploymentId,
            String railwayDeploymentId,
            String backendUrl
    ) {
        return deploymentService.markBackendDeployed(
                deploymentId,
                railwayDeploymentId,
                backendUrl
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeploymentResponse markFailed(
            Long deploymentId,
            String errorMessage
    ) {
        return deploymentService.markBackendFailed(
                deploymentId,
                errorMessage
        );
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeploymentResponse recordDeploymentId(
            Long deploymentId,
            String railwayDeploymentId
    ) {
        return deploymentService.recordBackendDeploymentId(
                deploymentId,
                railwayDeploymentId
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeploymentResponse markProvisioningFailed(
            Long deploymentId,
            String errorMessage
    ) {
        return deploymentService.markBackendProvisioningFailed(
                deploymentId,
                errorMessage
        );
    }

}
