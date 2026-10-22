
package com.aibuilder.deployment.service;

import com.aibuilder.build.entity.BuildRun;
import com.aibuilder.deployment.dto.DeploymentResponse;
import com.aibuilder.deployment.entity.Deployment;
import com.aibuilder.deployment.entity.DeploymentStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RailwayBackendOrchestrationServiceTest {

    @Mock
    private DeploymentService deploymentService;

    @Mock
    private RailwayBackendUrlService backendUrlService;

    @Mock
    private BackendDeploymentTransactionService transactionService;

    @InjectMocks
    private RailwayBackendOrchestrationService orchestrationService;

    @Test
    void shouldCompleteSuccessfulBackendDeployment() {

        Long deploymentId = 1L;
        Long buildRunId = 97L;

        String projectId = "railway-project-123";
        String serviceId = "railway-service-123";
        String environmentId = "railway-environment-123";
        String backendUrl = "https://example.up.railway.app";
        String railwayDeploymentId = "railway-deployment-123";

        BuildRun buildRun = new BuildRun();
        buildRun.setId(buildRunId);

        Deployment deployment = Deployment.builder()
                .id(deploymentId)
                .buildRun(buildRun)
                .status(DeploymentStatus.PENDING)
                .build();

        when(deploymentService.getDeploymentEntity(deploymentId))
                .thenReturn(deployment);

        when(deploymentService.uploadBackendToRailway(
                buildRunId,
                projectId,
                serviceId,
                environmentId
        )).thenReturn(railwayDeploymentId);

        DeploymentResponse expectedResponse =
                mock(DeploymentResponse.class);

        when(backendUrlService.resolveBackendUrl(
                projectId,
                environmentId,
                serviceId
        )).thenReturn(backendUrl);

        when(transactionService.markDeployed(
                deploymentId,
                railwayDeploymentId,
                backendUrl
        )).thenReturn(expectedResponse);

        DeploymentResponse actualResponse =
                orchestrationService.deployBackend(
                        deploymentId,
                        buildRunId,
                        projectId,
                        serviceId,
                        environmentId,
                        backendUrl
                );

        assertSame(expectedResponse, actualResponse);


        InOrder order = inOrder(
                deploymentService,
                transactionService,
                backendUrlService
        );

// 1. Validate deployment
        order.verify(deploymentService)
                .getDeploymentEntity(deploymentId);

// 2. Mark deployment as DEPLOYING
        order.verify(transactionService)
                .markDeploying(deploymentId, serviceId);

// 3. Upload backend to Railway
        order.verify(deploymentService)
                .uploadBackendToRailway(
                        buildRunId,
                        projectId,
                        serviceId,
                        environmentId
                );

// 4. Save Railway deployment ID
        order.verify(transactionService)
                .recordDeploymentId(
                        deploymentId,
                        railwayDeploymentId
                );

// 5. Resolve Railway backend URL
        order.verify(backendUrlService)
                .resolveBackendUrl(
                        projectId,
                        environmentId,
                        serviceId
                );

// 6. Mark backend as DEPLOYED
        order.verify(transactionService)
                .markDeployed(
                        deploymentId,
                        railwayDeploymentId,
                        backendUrl
                );


        verify(transactionService, never())
                .markFailed(
                        anyLong(),
                        anyString()
                );
    }


    @Test
    void shouldMarkBackendFailedWhenRailwayUploadFails() {

        Long deploymentId = 2L;
        Long buildRunId = 98L;

        String projectId = "railway-project-123";
        String serviceId = "railway-service-123";
        String environmentId = "railway-environment-123";
        String backendUrl = "https://example.up.railway.app";

        BuildRun buildRun = new BuildRun();
        buildRun.setId(buildRunId);

        Deployment deployment = Deployment.builder()
                .id(deploymentId)
                .buildRun(buildRun)
                .status(DeploymentStatus.PENDING)
                .build();

        when(deploymentService.getDeploymentEntity(deploymentId))
                .thenReturn(deployment);

        // Simulate Railway upload failure.
        when(deploymentService.uploadBackendToRailway(
                buildRunId,
                projectId,
                serviceId,
                environmentId
        )).thenThrow(
                new RuntimeException("Simulated Railway upload failure")
        );

        com.aibuilder.deployment.exception.DeploymentException exception =
                assertThrows(
                        com.aibuilder.deployment.exception.DeploymentException.class,
                        () -> orchestrationService.deployBackend(
                                deploymentId,
                                buildRunId,
                                projectId,
                                serviceId,
                                environmentId,
                                backendUrl
                        )
                );

        assertTrue(
                exception.getMessage().contains(
                        "Simulated Railway upload failure"
                )
        );

        InOrder order = inOrder(
                deploymentService,
                transactionService
        );

        order.verify(deploymentService)
                .getDeploymentEntity(deploymentId);

        order.verify(transactionService)
                .markDeploying(deploymentId, serviceId);

        order.verify(deploymentService)
                .uploadBackendToRailway(
                        buildRunId,
                        projectId,
                        serviceId,
                        environmentId
                );

        order.verify(transactionService)
                .markFailed(
                        deploymentId,
                        "Simulated Railway upload failure"
                );

        verify(transactionService, never())
                .markDeployed(
                        anyLong(),
                        anyString(),
                        any()
                );
    }


    @Test
    void shouldRejectDeploymentWithWrongBuildId() {

        Long deploymentId = 3L;
        Long actualBuildId = 97L;
        Long requestedBuildId = 99L;

        BuildRun buildRun = new BuildRun();
        buildRun.setId(actualBuildId);

        Deployment deployment = Deployment.builder()
                .id(deploymentId)
                .buildRun(buildRun)
                .status(DeploymentStatus.PENDING)
                .build();

        when(deploymentService.getDeploymentEntity(deploymentId))
                .thenReturn(deployment);

        com.aibuilder.deployment.exception.DeploymentException exception =
                assertThrows(
                        com.aibuilder.deployment.exception.DeploymentException.class,
                        () -> orchestrationService.deployBackend(
                                deploymentId,
                                requestedBuildId,
                                "railway-project-123",
                                "railway-service-123",
                                "railway-environment-123",
                                "https://example.up.railway.app"
                        )
                );

        assertEquals(
                "Deployment does not belong to the requested build.",
                exception.getMessage()
        );

        // No database status changes or Railway upload.
        verifyNoInteractions(transactionService);

        verify(
                deploymentService,
                never()
        ).uploadBackendToRailway(
                anyLong(),
                anyString(),
                anyString(),
                anyString()
        );
    }


    @Test
    void shouldNotUploadWhenMarkDeployingFails() {

        Long deploymentId = 4L;
        Long buildRunId = 100L;

        String projectId = "railway-project-123";
        String serviceId = "railway-service-123";
        String environmentId = "railway-environment-123";
        String backendUrl = "https://example.up.railway.app";

        BuildRun buildRun = new BuildRun();
        buildRun.setId(buildRunId);

        Deployment deployment = Deployment.builder()
                .id(deploymentId)
                .buildRun(buildRun)
                .status(DeploymentStatus.PENDING)
                .build();

        when(deploymentService.getDeploymentEntity(deploymentId))
                .thenReturn(deployment);

        // Simulate database transaction failure.
        doThrow(
                new RuntimeException("Database update failed")
        ).when(transactionService).markDeploying(
                deploymentId,
                serviceId
        );

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> orchestrationService.deployBackend(
                        deploymentId,
                        buildRunId,
                        projectId,
                        serviceId,
                        environmentId,
                        backendUrl
                )
        );

        assertEquals(
                "Database update failed",
                exception.getMessage()
        );

        // Railway upload must never start.
        verify(deploymentService, never())
                .uploadBackendToRailway(
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString()
                );

        // No success or failure transition should be attempted.
        verify(transactionService, never())
                .markDeployed(
                        anyLong(),
                        anyString(),
                        any()
                );

        verify(transactionService, never())
                .markFailed(
                        anyLong(),
                        anyString()
                );
    }


    @Test
    void shouldNotMarkRailwayDeploymentFailedWhenSuccessPersistenceFails() {

        Long deploymentId = 5L;
        Long buildRunId = 101L;

        String projectId = "railway-project-123";
        String serviceId = "railway-service-123";
        String environmentId = "railway-environment-123";
        String backendUrl = "https://example.up.railway.app";
        String railwayDeploymentId = "railway-deployment-123";

        BuildRun buildRun = new BuildRun();
        buildRun.setId(buildRunId);

        Deployment deployment = Deployment.builder()
                .id(deploymentId)
                .buildRun(buildRun)
                .status(DeploymentStatus.PENDING)
                .build();

        when(deploymentService.getDeploymentEntity(deploymentId))
                .thenReturn(deployment);

        // Railway deployment succeeds.
        when(deploymentService.uploadBackendToRailway(
                buildRunId,
                projectId,
                serviceId,
                environmentId
        )).thenReturn(railwayDeploymentId);

        when(backendUrlService.resolveBackendUrl(
                projectId,
                environmentId,
                serviceId
        )).thenReturn(backendUrl);

        // But saving DEPLOYED status fails.
        doThrow(
                new RuntimeException("Database unavailable")
        ).when(transactionService).markDeployed(
                deploymentId,
                railwayDeploymentId,
                backendUrl
        );

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> orchestrationService.deployBackend(
                        deploymentId,
                        buildRunId,
                        projectId,
                        serviceId,
                        environmentId,
                        backendUrl
                )
        );

        assertTrue(
                exception.getMessage().contains("Database unavailable")
        );

        // Railway succeeded, so it must not be marked FAILED.
        verify(transactionService, never())
                .markFailed(
                        anyLong(),
                        anyString()
                );

        verify(transactionService).markDeploying(
                deploymentId,
                serviceId
        );

        verify(transactionService).markDeployed(
                deploymentId,
                railwayDeploymentId,
                backendUrl
        );
    }


    @Test
    void shouldPreserveDeploymentIdWhenDomainLookupFails() {

        Long deploymentId = 6L;
        Long buildRunId = 102L;

        String projectId = "railway-project-123";
        String serviceId = "railway-service-123";
        String environmentId = "railway-environment-123";
        String railwayDeploymentId = "railway-deployment-456";

        BuildRun buildRun = new BuildRun();
        buildRun.setId(buildRunId);

        Deployment deployment = Deployment.builder()
                .id(deploymentId)
                .buildRun(buildRun)
                .status(DeploymentStatus.PENDING)
                .build();

        when(deploymentService.getDeploymentEntity(deploymentId))
                .thenReturn(deployment);

        when(deploymentService.uploadBackendToRailway(
                buildRunId,
                projectId,
                serviceId,
                environmentId
        )).thenReturn(railwayDeploymentId);

        when(backendUrlService.resolveBackendUrl(
                projectId,
                environmentId,
                serviceId
        )).thenThrow(
                new RuntimeException("Domain lookup failed")
        );

        var exception = assertThrows(
                com.aibuilder.deployment.exception.DeploymentException.class,
                () -> orchestrationService.deployBackend(
                        deploymentId,
                        buildRunId,
                        projectId,
                        serviceId,
                        environmentId,
                        null
                )
        );

        assertTrue(exception.getMessage().contains(
                "URL resolution failed"
        ));

        InOrder order = inOrder(
                transactionService,
                deploymentService,
                backendUrlService
        );

        order.verify(transactionService)
                .markDeploying(deploymentId, serviceId);

        order.verify(deploymentService)
                .uploadBackendToRailway(
                        buildRunId,
                        projectId,
                        serviceId,
                        environmentId
                );

        order.verify(transactionService)
                .recordDeploymentId(
                        deploymentId,
                        railwayDeploymentId
                );

        order.verify(backendUrlService)
                .resolveBackendUrl(
                        projectId,
                        environmentId,
                        serviceId
                );

        verify(transactionService, never())
                .markFailed(anyLong(), anyString());

        verify(transactionService, never())
                .markDeployed(
                        anyLong(),
                        anyString(),
                        any()
                );
    }
}
