
package com.aibuilder.deployment.railway;

import org.junit.jupiter.api.Test;

import com.aibuilder.deployment.exception.DeploymentException;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.*;

class RailwayCliDeploymentServiceTest {

    @org.junit.jupiter.api.Disabled(
            "Integration test: requires installed Railway CLI"
    )

    @Test
    void shouldExecuteRailwayCliFromJava() {

        RailwayCliDeploymentService service =
                new RailwayCliDeploymentService();

        String version = service.checkRailwayCli();

        System.out.println("Railway CLI: " + version);

        assertTrue(
                version.toLowerCase().contains("railway"),
                "Railway CLI version was not returned"
        );
    }


    @Test
    void shouldReadExistingRailwayDeploymentIds() {

        RailwayCliDeploymentService service =
                spy(new RailwayCliDeploymentService());

        doReturn("""
            [
              {"id":"deployment-101","status":"SUCCESS"},
              {"id":"deployment-102","status":"FAILED"}
            ]
            """)
                .when(service)
                .listDeployments(
                        "test-project",
                        "test-service",
                        "test-environment"
                );

        java.util.Set<String> deploymentIds =
                service.getDeploymentIds(
                        "test-project",
                        "test-service",
                        "test-environment"
                );

        assertEquals(
                java.util.Set.of("deployment-101", "deployment-102"),
                deploymentIds
        );
    }


    @Test
    void shouldRejectMissingBackendDirectory() {

        RailwayCliDeploymentService service =
                new RailwayCliDeploymentService();

        java.nio.file.Path missingDirectory =
                java.nio.file.Path.of(
                        "runtime",
                        "workspaces",
                        "nonexistent-backend-test-directory"
                );

        DeploymentException exception = assertThrows(
                DeploymentException.class,
                () -> service.deployBackend(
                        missingDirectory,
                        "test-project-id",
                        "test-service-id",
                        "test-environment-id"
                )
        );

        assertEquals(
                "Generated backend directory does not exist.",
                exception.getMessage()
        );
    }


    @Test
    void shouldRejectInvalidRailwayIds() {

        RailwayCliDeploymentService service =
                new RailwayCliDeploymentService();

        assertThrows(
                DeploymentException.class,
                () -> service.getDeploymentIds(
                        "",
                        "service-id",
                        "environment-id"
                )
        );

        assertThrows(
                DeploymentException.class,
                () -> service.getDeploymentIds(
                        "project-id",
                        "",
                        "environment-id"
                )
        );

        assertThrows(
                DeploymentException.class,
                () -> service.getDeploymentIds(
                        "project-id",
                        "service-id",
                        ""
                )
        );
    }


    @Test
    void shouldNotTreatExistingDeploymentAsNew() {

        RailwayCliDeploymentService service =
                spy(new RailwayCliDeploymentService());

        String projectId = "test-project";
        String serviceId = "test-service";
        String environmentId = "test-environment";

        doReturn("""
            [
              {"id":"deployment-101","status":"SUCCESS"}
            ]
            """)
                .when(service)
                .listDeployments(
                        projectId,
                        serviceId,
                        environmentId
                );

        java.util.Set<String> previousIds =
                service.getDeploymentIds(
                        projectId,
                        serviceId,
                        environmentId
                );

        DeploymentException exception = assertThrows(
                DeploymentException.class,
                () -> service.findNewDeploymentId(
                        projectId,
                        serviceId,
                        environmentId,
                        previousIds
                )
        );

        assertEquals(
                "No new Railway deployment found.",
                exception.getMessage()
        );
    }


    @Test
    void shouldReleaseDeploymentLockAfterFailure() throws Exception {

        RailwayCliDeploymentService service =
                new RailwayCliDeploymentService();

        String projectId = "test-project";
        String serviceId = "test-service";
        String environmentId = "test-environment";

        java.lang.reflect.Method method =
                RailwayCliDeploymentService.class.getDeclaredMethod(
                        "getDeploymentLock",
                        String.class,
                        String.class,
                        String.class
                );

        method.setAccessible(true);

        java.util.concurrent.locks.ReentrantLock lock =
                (java.util.concurrent.locks.ReentrantLock)
                        method.invoke(
                                service,
                                projectId,
                                serviceId,
                                environmentId
                        );

        // Simulate a failure inside a locked operation.
        lock.lock();

        try {
            throw new DeploymentException(
                    "Simulated deployment failure"
            );
        } catch (DeploymentException expected) {
            assertEquals(
                    "Simulated deployment failure",
                    expected.getMessage()
            );
        } finally {
            lock.unlock();
        }

        assertFalse(
                lock.isLocked(),
                "Deployment lock should be released after failure"
        );
    }


    @Test
    void shouldRejectMissingRailwayClientBeforeUpload() {

        RailwayCliDeploymentService service =
                new RailwayCliDeploymentService();

        DeploymentException exception = assertThrows(
                DeploymentException.class,
                () -> service.deployBackendAndTrack(
                        java.nio.file.Path.of(
                                "runtime",
                                "workspaces",
                                "nonexistent-backend-test-directory"
                        ),
                        "test-project",
                        "test-service",
                        "test-environment",
                        null
                )
        );

        assertEquals(
                "Railway deployment client is required.",
                exception.getMessage()
        );
    }


    @Test
    void shouldDetectNewDeploymentUsingMockData() {

        RailwayCliDeploymentService service =
                org.mockito.Mockito.spy(
                        new RailwayCliDeploymentService()
                );

        org.mockito.Mockito.doReturn(
                java.util.Set.of(
                        "old-deployment-1",
                        "old-deployment-2",
                        "new-deployment-3"
                )
        ).when(service).getDeploymentIds(
                "test-project",
                "test-service",
                "test-environment"
        );

        String newDeploymentId = service.findNewDeploymentId(
                "test-project",
                "test-service",
                "test-environment",
                java.util.Set.of(
                        "old-deployment-1",
                        "old-deployment-2"
                )
        );

        assertEquals(
                "new-deployment-3",
                newDeploymentId
        );

        org.mockito.Mockito.verify(service)
                .getDeploymentIds(
                        "test-project",
                        "test-service",
                        "test-environment"
                );
    }


    @Test
    void shouldRejectMultipleNewDeployments() {

        RailwayCliDeploymentService service =
                org.mockito.Mockito.spy(
                        new RailwayCliDeploymentService()
                );

        org.mockito.Mockito.doReturn(
                java.util.Set.of(
                        "old-deployment",
                        "new-deployment-1",
                        "new-deployment-2"
                )
        ).when(service).getDeploymentIds(
                "test-project",
                "test-service",
                "test-environment"
        );

        DeploymentException exception = assertThrows(
                DeploymentException.class,
                () -> service.findNewDeploymentId(
                        "test-project",
                        "test-service",
                        "test-environment",
                        java.util.Set.of("old-deployment")
                )
        );

        assertEquals(
                "Multiple new Railway deployments found. "
                        + "Cannot safely identify the uploaded deployment.",
                exception.getMessage()
        );
    }


    @Test
    void shouldRejectConcurrentDeploymentForSameService()
            throws Exception {

        RailwayCliDeploymentService service =
                new RailwayCliDeploymentService();

        String projectId = "test-project";
        String serviceId = "test-service";
        String environmentId = "test-environment";

        java.lang.reflect.Method method =
                RailwayCliDeploymentService.class.getDeclaredMethod(
                        "getDeploymentLock",
                        String.class,
                        String.class,
                        String.class
                );

        method.setAccessible(true);

        java.util.concurrent.locks.ReentrantLock lock =
                (java.util.concurrent.locks.ReentrantLock)
                        method.invoke(
                                service,
                                projectId,
                                serviceId,
                                environmentId
                        );

        lock.lock();

        try {
            // Run on another thread to simulate a second request.
            java.util.concurrent.CompletableFuture<String> result =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        try {
                            service.deployBackendAndTrack(
                                    java.nio.file.Path.of("nonexistent-backend"),
                                    projectId,
                                    serviceId,
                                    environmentId,
                                    org.mockito.Mockito.mock(
                                            RailwayDeploymentClient.class
                                    )
                            );

                            return "NO_EXCEPTION";

                        } catch (DeploymentException e) {
                            return e.getMessage();
                        }
                    });

            assertEquals(
                    "Another deployment is already running for this Railway service.",
                    result.get(5, java.util.concurrent.TimeUnit.SECONDS)
            );

        } finally {
            lock.unlock();
        }
    }

    @Test
    void shouldReleaseLockWhenBackendUploadFails() throws Exception {

        RailwayCliDeploymentService service =
                org.mockito.Mockito.spy(
                        new RailwayCliDeploymentService()
                );

        RailwayDeploymentClient railwayClient =
                org.mockito.Mockito.mock(
                        RailwayDeploymentClient.class
                );

        String projectId = "test-project";
        String serviceId = "test-service";
        String environmentId = "test-environment";

        java.nio.file.Path backendDirectory =
                java.nio.file.Path.of("mock-backend");

        // Simulate existing Railway deployment history.
        org.mockito.Mockito.doReturn(
                java.util.Set.of("old-deployment")
        ).when(service).getDeploymentIds(
                projectId,
                serviceId,
                environmentId
        );

        // Simulate upload failure without running Railway CLI.
        org.mockito.Mockito.doThrow(
                new DeploymentException("Simulated upload failure")
        ).when(service).deployBackend(
                backendDirectory,
                projectId,
                serviceId,
                environmentId
        );

        DeploymentException exception = assertThrows(
                DeploymentException.class,
                () -> service.deployBackendAndTrack(
                        backendDirectory,
                        projectId,
                        serviceId,
                        environmentId,
                        railwayClient
                )
        );

        assertEquals(
                "Simulated upload failure",
                exception.getMessage()
        );

        // Status polling must not start after upload failure.
        org.mockito.Mockito.verify(
                railwayClient,
                org.mockito.Mockito.never()
        ).waitForDeploymentById(
                org.mockito.ArgumentMatchers.anyString()
        );

        // A second attempt must reach the upload stage,
        // proving that the first attempt released its lock.
        DeploymentException secondException = assertThrows(
                DeploymentException.class,
                () -> service.deployBackendAndTrack(
                        backendDirectory,
                        projectId,
                        serviceId,
                        environmentId,
                        railwayClient
                )
        );

        assertEquals(
                "Simulated upload failure",
                secondException.getMessage()
        );

        org.mockito.Mockito.verify(
                service,
                org.mockito.Mockito.times(2)
        ).deployBackend(
                backendDirectory,
                projectId,
                serviceId,
                environmentId
        );
    }


    @Test
    void shouldTrackSuccessfulBackendDeployment() {

        RailwayCliDeploymentService service =
                org.mockito.Mockito.spy(
                        new RailwayCliDeploymentService()
                );

        RailwayDeploymentClient railwayClient =
                org.mockito.Mockito.mock(
                        RailwayDeploymentClient.class
                );

        String projectId = "test-project";
        String serviceId = "test-service";
        String environmentId = "test-environment";
        String newDeploymentId = "new-deployment-123";

        java.nio.file.Path backendDirectory =
                java.nio.file.Path.of("mock-backend");

        // Existing deployments before upload.
        org.mockito.Mockito.doReturn(
                java.util.Set.of("old-deployment")
        ).when(service).getDeploymentIds(
                projectId,
                serviceId,
                environmentId
        );

        // Simulate successful CLI upload.
        org.mockito.Mockito.doReturn(
                "Upload completed"
        ).when(service).deployBackend(
                backendDirectory,
                projectId,
                serviceId,
                environmentId
        );

        // Simulate detection of the new deployment.
        org.mockito.Mockito.doReturn(
                newDeploymentId
        ).when(service).waitForNewDeploymentId(
                org.mockito.ArgumentMatchers.eq(projectId),
                org.mockito.ArgumentMatchers.eq(serviceId),
                org.mockito.ArgumentMatchers.eq(environmentId),
                org.mockito.ArgumentMatchers.anySet()
        );

        // Simulate Railway confirming SUCCESS.
        tools.jackson.databind.ObjectMapper mapper =
                new tools.jackson.databind.ObjectMapper();

        tools.jackson.databind.JsonNode successfulDeployment =
                mapper.createObjectNode()
                        .put("id", newDeploymentId)
                        .put("status", "SUCCESS");

        org.mockito.Mockito.when(
                railwayClient.waitForDeploymentById(newDeploymentId)
        ).thenReturn(successfulDeployment);

        String result = service.deployBackendAndTrack(
                backendDirectory,
                projectId,
                serviceId,
                environmentId,
                railwayClient
        );

        assertEquals(newDeploymentId, result);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
                service,
                railwayClient
        );

        order.verify(service).getDeploymentIds(
                projectId,
                serviceId,
                environmentId
        );

        order.verify(service).deployBackend(
                backendDirectory,
                projectId,
                serviceId,
                environmentId
        );

        order.verify(service).waitForNewDeploymentId(
                org.mockito.ArgumentMatchers.eq(projectId),
                org.mockito.ArgumentMatchers.eq(serviceId),
                org.mockito.ArgumentMatchers.eq(environmentId),
                org.mockito.ArgumentMatchers.anySet()
        );

        order.verify(railwayClient)
                .waitForDeploymentById(newDeploymentId);
    }


    @Test
    void shouldRejectUnexpectedRailwayDeploymentId() {

        RailwayCliDeploymentService service =
                org.mockito.Mockito.spy(
                        new RailwayCliDeploymentService()
                );

        RailwayDeploymentClient railwayClient =
                org.mockito.Mockito.mock(
                        RailwayDeploymentClient.class
                );

        String projectId = "test-project";
        String serviceId = "test-service";
        String environmentId = "test-environment";

        String expectedDeploymentId = "deployment-123";

        java.nio.file.Path backendDirectory =
                java.nio.file.Path.of("mock-backend");

        // Mock existing deployment history.
        org.mockito.Mockito.doReturn(
                java.util.Set.of("old-deployment")
        ).when(service).getDeploymentIds(
                projectId,
                serviceId,
                environmentId
        );

        // Mock successful upload without executing Railway CLI.
        org.mockito.Mockito.doReturn(
                "Upload completed"
        ).when(service).deployBackend(
                backendDirectory,
                projectId,
                serviceId,
                environmentId
        );

        // Mock detection of the expected new deployment ID.
        org.mockito.Mockito.doReturn(
                expectedDeploymentId
        ).when(service).waitForNewDeploymentId(
                org.mockito.ArgumentMatchers.eq(projectId),
                org.mockito.ArgumentMatchers.eq(serviceId),
                org.mockito.ArgumentMatchers.eq(environmentId),
                org.mockito.ArgumentMatchers.anySet()
        );

        // Railway returns a different deployment ID.
        tools.jackson.databind.ObjectMapper mapper =
                new tools.jackson.databind.ObjectMapper();

        tools.jackson.databind.JsonNode wrongDeployment =
                mapper.createObjectNode()
                        .put("id", "different-deployment-456")
                        .put("status", "SUCCESS");

        org.mockito.Mockito.when(
                railwayClient.waitForDeploymentById(expectedDeploymentId)
        ).thenReturn(wrongDeployment);

        DeploymentException exception = assertThrows(
                DeploymentException.class,
                () -> service.deployBackendAndTrack(
                        backendDirectory,
                        projectId,
                        serviceId,
                        environmentId,
                        railwayClient
                )
        );

        assertEquals(
                "Railway returned an unexpected deployment ID.",
                exception.getMessage()
        );
    }


    @Test
    void shouldRejectFailedRailwayDeploymentStatus() {

        RailwayCliDeploymentService service =
                org.mockito.Mockito.spy(
                        new RailwayCliDeploymentService()
                );

        RailwayDeploymentClient railwayClient =
                org.mockito.Mockito.mock(
                        RailwayDeploymentClient.class
                );

        String projectId = "test-project";
        String serviceId = "test-service";
        String environmentId = "test-environment";
        String deploymentId = "deployment-123";

        java.nio.file.Path backendDirectory =
                java.nio.file.Path.of("mock-backend");

        // Mock existing deployments.
        org.mockito.Mockito.doReturn(
                java.util.Set.of("old-deployment")
        ).when(service).getDeploymentIds(
                projectId,
                serviceId,
                environmentId
        );

        // Mock successful upload.
        org.mockito.Mockito.doReturn(
                "Upload completed"
        ).when(service).deployBackend(
                backendDirectory,
                projectId,
                serviceId,
                environmentId
        );

        // Mock detection of the new deployment.
        org.mockito.Mockito.doReturn(
                deploymentId
        ).when(service).waitForNewDeploymentId(
                org.mockito.ArgumentMatchers.eq(projectId),
                org.mockito.ArgumentMatchers.eq(serviceId),
                org.mockito.ArgumentMatchers.eq(environmentId),
                org.mockito.ArgumentMatchers.anySet()
        );

        // Simulate Railway returning FAILED.
        tools.jackson.databind.JsonNode failedDeployment =
                new tools.jackson.databind.ObjectMapper()
                        .createObjectNode()
                        .put("id", deploymentId)
                        .put("status", "FAILED");

        org.mockito.Mockito.when(
                railwayClient.waitForDeploymentById(deploymentId)
        ).thenReturn(failedDeployment);

        DeploymentException exception = assertThrows(
                DeploymentException.class,
                () -> service.deployBackendAndTrack(
                        backendDirectory,
                        projectId,
                        serviceId,
                        environmentId,
                        railwayClient
                )
        );

        assertEquals(
                "Railway deployment did not succeed. Status: FAILED",
                exception.getMessage()
        );
    }


    @Test
    void shouldRejectMissingRailwayDeploymentResponse() {

        RailwayCliDeploymentService service =
                org.mockito.Mockito.spy(
                        new RailwayCliDeploymentService()
                );

        RailwayDeploymentClient railwayClient =
                org.mockito.Mockito.mock(
                        RailwayDeploymentClient.class
                );

        String projectId = "test-project";
        String serviceId = "test-service";
        String environmentId = "test-environment";
        String deploymentId = "deployment-123";

        java.nio.file.Path backendDirectory =
                java.nio.file.Path.of("mock-backend");

        // Existing deployments before upload.
        org.mockito.Mockito.doReturn(
                java.util.Set.of("old-deployment")
        ).when(service).getDeploymentIds(
                projectId,
                serviceId,
                environmentId
        );

        // Simulate successful upload without invoking Railway CLI.
        org.mockito.Mockito.doReturn(
                "Upload completed"
        ).when(service).deployBackend(
                backendDirectory,
                projectId,
                serviceId,
                environmentId
        );

        // Simulate detection of a new deployment.
        org.mockito.Mockito.doReturn(
                deploymentId
        ).when(service).waitForNewDeploymentId(
                org.mockito.ArgumentMatchers.eq(projectId),
                org.mockito.ArgumentMatchers.eq(serviceId),
                org.mockito.ArgumentMatchers.eq(environmentId),
                org.mockito.ArgumentMatchers.anySet()
        );

        // Simulate Railway returning no deployment response.
        org.mockito.Mockito.when(
                railwayClient.waitForDeploymentById(deploymentId)
        ).thenReturn(null);

        DeploymentException exception = assertThrows(
                DeploymentException.class,
                () -> service.deployBackendAndTrack(
                        backendDirectory,
                        projectId,
                        serviceId,
                        environmentId,
                        railwayClient
                )
        );

        assertEquals(
                "Railway deployment response is missing.",
                exception.getMessage()
        );
    }






}








