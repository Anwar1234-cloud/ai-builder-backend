
package com.aibuilder.deployment.controller;

import com.aibuilder.deployment.dto.DeploymentResponse;
import com.aibuilder.deployment.service.DeploymentAccessService;
import com.aibuilder.deployment.service.DeploymentService;
import com.aibuilder.deployment.service.RailwayAutomaticDeploymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/deployments")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;
    private final DeploymentAccessService accessService;
    private final RailwayAutomaticDeploymentService railwayAutomaticDeploymentService;

    @PostMapping
    public ResponseEntity<DeploymentResponse> createDeployment(
            @PathVariable Long projectId
    ) {
        accessService.requireProjectOwner(projectId);

        DeploymentResponse response =
                deploymentService.createDeployment(projectId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/{deploymentId}/backend")
    public ResponseEntity<DeploymentResponse> deployBackend(
            @PathVariable Long projectId,
            @PathVariable Long deploymentId,
            @RequestParam Long buildRunId
    ) {
        accessService.requireProjectOwner(projectId);

        DeploymentResponse response =
                railwayAutomaticDeploymentService
                        .deployUsingExistingResources(
                                projectId,
                                deploymentId,
                                buildRunId,
                                null
                        );

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<DeploymentResponse>> getDeployments(
            @PathVariable Long projectId
    ) {
        accessService.requireProjectOwner(projectId);

        return ResponseEntity.ok(
                deploymentService.getDeployments(projectId)
        );
    }

    @GetMapping("/latest")
    public ResponseEntity<DeploymentResponse> getLatestDeployment(
            @PathVariable Long projectId
    ) {
        accessService.requireProjectOwner(projectId);

        return ResponseEntity.ok(
                deploymentService.getLatestDeployment(projectId)
        );
    }
}
