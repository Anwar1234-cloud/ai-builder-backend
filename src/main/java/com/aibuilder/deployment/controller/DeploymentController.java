package com.aibuilder.deployment.controller;

import com.aibuilder.deployment.dto.DeploymentResponse;
import com.aibuilder.deployment.service.DeploymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/projects/{projectId}/deployments"
)
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;


    @PostMapping
    public ResponseEntity<DeploymentResponse>
    createDeployment(
            @PathVariable Long projectId
    ) {

        DeploymentResponse response =
                deploymentService
                        .createDeployment(projectId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    @GetMapping
    public ResponseEntity<List<DeploymentResponse>>
    getDeployments(
            @PathVariable Long projectId
    ) {

        return ResponseEntity.ok(
                deploymentService
                        .getDeployments(projectId)
        );
    }


    @GetMapping("/latest")
    public ResponseEntity<DeploymentResponse>
    getLatestDeployment(
            @PathVariable Long projectId
    ) {

        return ResponseEntity.ok(
                deploymentService
                        .getLatestDeployment(projectId)
        );
    }
}