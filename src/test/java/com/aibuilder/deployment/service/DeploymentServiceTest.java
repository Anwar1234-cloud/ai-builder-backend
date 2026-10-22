
package com.aibuilder.deployment.service;

import com.aibuilder.build.repository.BuildRunRepository;
import com.aibuilder.deployment.entity.Deployment;
import com.aibuilder.deployment.entity.DeploymentProvider;
import com.aibuilder.deployment.entity.DeploymentStatus;
import com.aibuilder.deployment.repository.DeploymentRepository;
import com.aibuilder.deployment.railway.RailwayCliDeploymentService;
import com.aibuilder.deployment.railway.RailwayDeploymentClient;
import com.aibuilder.deployment.vercel.VercelDeploymentClient;
import com.aibuilder.project.repository.ProjectRepository;
import com.aibuilder.project.entity.Project;
import com.aibuilder.build.entity.BuildRun;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeploymentServiceTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private BuildRunRepository buildRunRepository;

    @Mock
    private VercelDeploymentClient vercelDeploymentClient;

    @Mock
    private RailwayCliDeploymentService railwayCliDeploymentService;

    @Mock
    private RailwayDeploymentClient railwayDeploymentClient;

    @InjectMocks
    private DeploymentService deploymentService;

    @Test
    void shouldMarkBackendAsDeploying() {

        Long deploymentId = 1L;
        String railwayServiceId = "railway-service-123";

        Project project = new Project();
        project.setId(1L);

        BuildRun buildRun = new BuildRun();
        buildRun.setId(97L);

        Deployment deployment = Deployment.builder()
                .id(deploymentId)
                .project(project)
                .buildRun(buildRun)
                .provider(DeploymentProvider.VERCEL)
                .status(DeploymentStatus.PENDING)
                .build();

        when(deploymentRepository.findById(deploymentId))
                .thenReturn(Optional.of(deployment));

        when(deploymentRepository.save(any(Deployment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        deploymentService.markBackendDeploying(
                deploymentId,
                railwayServiceId
        );

        assertEquals(
                DeploymentProvider.RAILWAY,
                deployment.getBackendProvider()
        );

        assertEquals(
                railwayServiceId,
                deployment.getBackendServiceId()
        );

        assertEquals(
                DeploymentStatus.DEPLOYING,
                deployment.getBackendStatus()
        );

        assertNotNull(deployment.getStartedAt());

        verify(deploymentRepository).save(deployment);
        verifyNoInteractions(railwayCliDeploymentService);
    }


    @Test
    void shouldMarkBackendAsDeployed() {

        Long deploymentId = 2L;
        String railwayDeploymentId = "railway-deployment-123";
        String backendUrl = "https://example-backend.up.railway.app";

        Project project = new Project();
        project.setId(1L);

        BuildRun buildRun = new BuildRun();
        buildRun.setId(97L);

        Deployment deployment = Deployment.builder()
                .id(deploymentId)
                .project(project)
                .buildRun(buildRun)
                .provider(DeploymentProvider.VERCEL)
                .status(DeploymentStatus.DEPLOYING)
                .backendProvider(DeploymentProvider.RAILWAY)
                .backendServiceId("railway-service-123")
                .backendStatus(DeploymentStatus.DEPLOYING)
                .build();

        when(deploymentRepository.findById(deploymentId))
                .thenReturn(Optional.of(deployment));

        when(deploymentRepository.save(any(Deployment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        deploymentService.markBackendDeployed(
                deploymentId,
                railwayDeploymentId,
                backendUrl
        );

        assertEquals(
                DeploymentStatus.DEPLOYED,
                deployment.getBackendStatus()
        );

        assertEquals(
                railwayDeploymentId,
                deployment.getBackendDeploymentId()
        );

        assertEquals(
                backendUrl,
                deployment.getBackendUrl()
        );

        assertEquals(
                DeploymentProvider.RAILWAY,
                deployment.getBackendProvider()
        );

        verify(deploymentRepository).save(deployment);
        verifyNoInteractions(railwayCliDeploymentService);
    }


    @Test
    void shouldMarkBackendAsFailed() {

        Long deploymentId = 3L;
        String errorMessage = "Railway backend build failed.";

        Project project = new Project();
        project.setId(1L);

        BuildRun buildRun = new BuildRun();
        buildRun.setId(97L);

        Deployment deployment = Deployment.builder()
                .id(deploymentId)
                .project(project)
                .buildRun(buildRun)
                .provider(DeploymentProvider.VERCEL)
                .status(DeploymentStatus.DEPLOYING)
                .backendProvider(DeploymentProvider.RAILWAY)
                .backendServiceId("railway-service-123")
                .backendStatus(DeploymentStatus.DEPLOYING)
                .build();

        when(deploymentRepository.findById(deploymentId))
                .thenReturn(Optional.of(deployment));

        when(deploymentRepository.save(any(Deployment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        deploymentService.markBackendFailed(
                deploymentId,
                errorMessage
        );

        assertEquals(
                DeploymentStatus.FAILED,
                deployment.getBackendStatus()
        );

        assertEquals(
                errorMessage,
                deployment.getErrorMessage()
        );

        assertNotNull(
                deployment.getCompletedAt()
        );

        assertEquals(
                DeploymentProvider.RAILWAY,
                deployment.getBackendProvider()
        );

        verify(deploymentRepository).save(deployment);

        verifyNoInteractions(
                railwayCliDeploymentService,
                railwayDeploymentClient
        );
    }


}
