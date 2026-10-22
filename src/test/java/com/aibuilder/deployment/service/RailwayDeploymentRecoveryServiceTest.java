
package com.aibuilder.deployment.service;

import com.aibuilder.deployment.dto.DeploymentResponse;
import com.aibuilder.deployment.entity.Deployment;
import com.aibuilder.deployment.entity.DeploymentProvider;
import com.aibuilder.deployment.entity.DeploymentStatus;
import com.aibuilder.deployment.entity.RailwayProjectResources;
import com.aibuilder.deployment.exception.DeploymentException;
import com.aibuilder.deployment.railway.RailwayDeploymentClient;
import com.aibuilder.deployment.repository.RailwayProjectResourcesRepository;
import com.aibuilder.project.entity.Project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RailwayDeploymentRecoveryServiceTest {

    @Mock
    private DeploymentService deploymentService;

    @Mock
    private BackendDeploymentTransactionService transactionService;

    @Mock
    private RailwayDeploymentClient railwayClient;

    @Mock
    private RailwayBackendUrlService backendUrlService;

    @Mock
    private RailwayProjectResourcesRepository resourcesRepository;

    @InjectMocks
    private RailwayDeploymentRecoveryService recoveryService;

    private static final Long DEPLOYMENT_ID = 10L;
    private static final Long PROJECT_ID = 3L;

    private static final String SERVICE_ID = "railway-service-123";
    private static final String ENVIRONMENT_ID = "railway-env-123";
    private static final String RAILWAY_DEPLOYMENT_ID = "railway-deploy-123";
    private static final String BACKEND_URL =
            "https://example.up.railway.app";
    private static final String RAILWAY_PROJECT_ID =
            "railway-project-123";

    private Deployment createDeployment(
            DeploymentProvider provider,
            DeploymentStatus status,
            String railwayDeploymentId
    ) {
        Project project = new Project();
        project.setId(PROJECT_ID);

        return Deployment.builder()
                .id(DEPLOYMENT_ID)
                .project(project)
                .backendProvider(provider)
                .backendStatus(status)
                .backendServiceId(SERVICE_ID)
                .backendDeploymentId(railwayDeploymentId)
                .build();
    }

    private RailwayProjectResources createResources() {
        return RailwayProjectResources.builder()
                .railwayProjectId(RAILWAY_PROJECT_ID)
                .railwayEnvironmentId(ENVIRONMENT_ID)
                .railwayBackendServiceId(SERVICE_ID)
                .build();
    }

    private JsonNode railwayStatus(
            String deploymentId,
            String status
    ) {
        ObjectMapper mapper = new ObjectMapper();

        return mapper.createObjectNode()
                .put("id", deploymentId)
                .put("status", status);
    }

    @Test
    void shouldRecoverSuccessfulDeployment() {

        Deployment deployment = createDeployment(
                DeploymentProvider.RAILWAY,
                DeploymentStatus.DEPLOYING,
                RAILWAY_DEPLOYMENT_ID
        );

        when(deploymentService.getDeploymentEntity(DEPLOYMENT_ID))
                .thenReturn(deployment);

        when(resourcesRepository.findByProjectId(PROJECT_ID))
                .thenReturn(Optional.of(createResources()));

        when(railwayClient.getDeploymentStatus(RAILWAY_DEPLOYMENT_ID))
                .thenReturn(railwayStatus(
                        RAILWAY_DEPLOYMENT_ID,
                        "SUCCESS"
                ));

        when(backendUrlService.resolveBackendUrl(
                RAILWAY_PROJECT_ID,
                ENVIRONMENT_ID,
                SERVICE_ID
        )).thenReturn(BACKEND_URL);

        DeploymentResponse expected = mock(DeploymentResponse.class);

        when(transactionService.markDeployed(
                DEPLOYMENT_ID,
                RAILWAY_DEPLOYMENT_ID,
                BACKEND_URL
        )).thenReturn(expected);

        DeploymentResponse actual =
                recoveryService.recoverDeployment(DEPLOYMENT_ID);

        assertSame(expected, actual);

        verify(transactionService).markDeployed(
                DEPLOYMENT_ID,
                RAILWAY_DEPLOYMENT_ID,
                BACKEND_URL
        );

        verify(backendUrlService).resolveBackendUrl(
                RAILWAY_PROJECT_ID,
                ENVIRONMENT_ID,
                SERVICE_ID
        );
    }

    @Test
    void shouldRejectNonRailwayDeployment() {

        when(deploymentService.getDeploymentEntity(DEPLOYMENT_ID))
                .thenReturn(createDeployment(
                        DeploymentProvider.VERCEL,
                        DeploymentStatus.DEPLOYING,
                        RAILWAY_DEPLOYMENT_ID
                ));

        assertThrows(
                DeploymentException.class,
                () -> recoveryService.recoverDeployment(DEPLOYMENT_ID)
        );

        verifyNoInteractions(railwayClient, transactionService);
    }

    @Test
    void shouldRejectDeploymentWithoutSavedId() {

        when(deploymentService.getDeploymentEntity(DEPLOYMENT_ID))
                .thenReturn(createDeployment(
                        DeploymentProvider.RAILWAY,
                        DeploymentStatus.DEPLOYING,
                        null
                ));

        assertThrows(
                DeploymentException.class,
                () -> recoveryService.recoverDeployment(DEPLOYMENT_ID)
        );

        verifyNoInteractions(railwayClient, transactionService);
    }

    @Test
    void shouldNotRecoverFailedRailwayDeployment() {

        when(deploymentService.getDeploymentEntity(DEPLOYMENT_ID))
                .thenReturn(createDeployment(
                        DeploymentProvider.RAILWAY,
                        DeploymentStatus.DEPLOYING,
                        RAILWAY_DEPLOYMENT_ID
                ));

        when(resourcesRepository.findByProjectId(PROJECT_ID))
                .thenReturn(Optional.of(createResources()));

        when(railwayClient.getDeploymentStatus(RAILWAY_DEPLOYMENT_ID))
                .thenReturn(railwayStatus(
                        RAILWAY_DEPLOYMENT_ID,
                        "FAILED"
                ));

        assertThrows(
                DeploymentException.class,
                () -> recoveryService.recoverDeployment(DEPLOYMENT_ID)
        );

        verifyNoInteractions(backendUrlService, transactionService);
    }

    @Test
    void shouldNotMarkDeployedWhenDomainLookupFails() {

        when(deploymentService.getDeploymentEntity(DEPLOYMENT_ID))
                .thenReturn(createDeployment(
                        DeploymentProvider.RAILWAY,
                        DeploymentStatus.DEPLOYING,
                        RAILWAY_DEPLOYMENT_ID
                ));

        when(resourcesRepository.findByProjectId(PROJECT_ID))
                .thenReturn(Optional.of(createResources()));

        when(railwayClient.getDeploymentStatus(RAILWAY_DEPLOYMENT_ID))
                .thenReturn(railwayStatus(
                        RAILWAY_DEPLOYMENT_ID,
                        "SUCCESS"
                ));

        when(backendUrlService.resolveBackendUrl(
                RAILWAY_PROJECT_ID,
                ENVIRONMENT_ID,
                SERVICE_ID
        )).thenThrow(
                new DeploymentException("Domain lookup failed")
        );

        assertThrows(
                DeploymentException.class,
                () -> recoveryService.recoverDeployment(DEPLOYMENT_ID)
        );

        verify(transactionService, never())
                .markDeployed(
                        anyLong(),
                        anyString(),
                        any()
                );
    }
}
