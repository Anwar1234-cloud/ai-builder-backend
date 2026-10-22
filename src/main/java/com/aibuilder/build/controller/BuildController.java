package com.aibuilder.build.controller;

import com.aibuilder.build.entity.BuildRun;
import com.aibuilder.build.service.BuildService;
import com.aibuilder.build.service.ProjectBootstrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.aibuilder.fullstack.validation.FullStackBuildValidationService;
import com.aibuilder.fullstack.validation.FullStackFunctionalValidationService;
import com.aibuilder.project.service.ProjectService;
import com.aibuilder.project.entity.ProjectType;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/builds")
@RequiredArgsConstructor
public class BuildController {

    private final BuildService buildService;
    private final ProjectBootstrapService projectBootstrapService;
    private final FullStackBuildValidationService fullStackBuildValidationService;
    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<BuildRun> createBuild(
            @PathVariable Long projectId,
            @RequestParam(required = false) Long agentRunId,
            @RequestParam(defaultValue = "npm run build") String command
    ) {
        return ResponseEntity.ok(
                buildService.createBuild(
                        projectId,
                        agentRunId,
                        command
                )
        );
    }

    @PostMapping("/{buildId}/start")
    public ResponseEntity<BuildRun> startBuild(
            @PathVariable Long projectId,
            @PathVariable Long buildId
    ) {
        return ResponseEntity.ok(
                buildService.startBuild(buildId)
        );
    }

    @PostMapping("/{buildId}/complete")
    public ResponseEntity<BuildRun> completeBuild(
            @PathVariable Long projectId,
            @PathVariable Long buildId,
            @RequestParam(required = false, defaultValue = "") String output
    ) {
        return ResponseEntity.ok(
                buildService.completeBuild(
                        buildId,
                        output
                )
        );
    }

    @PostMapping("/{buildId}/fail")
    public ResponseEntity<BuildRun> failBuild(
            @PathVariable Long projectId,
            @PathVariable Long buildId,
            @RequestParam(required = false, defaultValue = "") String output,
            @RequestParam String errorOutput
    ) {
        return ResponseEntity.ok(
                buildService.failBuild(
                        buildId,
                        output,
                        errorOutput
                )
        );
    }





    @GetMapping("/{buildId}")
    public ResponseEntity<BuildRun> getBuild(
            @PathVariable Long projectId,
            @PathVariable Long buildId
    ) {
        return ResponseEntity.ok(
                buildService.getBuild(buildId)
        );
    }

    @GetMapping
    public ResponseEntity<List<BuildRun>> getBuilds(
            @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(
                buildService.getBuilds(projectId)
        );
    }

    @PostMapping("/{buildId}/workspace")
    public ResponseEntity<BuildRun> materializeWorkspace(
            @PathVariable Long projectId,
            @PathVariable Long buildId
    ) {
        return ResponseEntity.ok(
                buildService.materializeBuildWorkspace(
                        buildId
                )
        );
    }

    @PostMapping("/{buildId}/execute")
    public ResponseEntity<BuildRun> executeBuild(
            @PathVariable Long projectId,
            @PathVariable Long buildId
    ) {
        return ResponseEntity.ok(
                buildService.executeBuild(buildId)
        );
    }

    @PostMapping("/bootstrap")
    public ResponseEntity<Void> bootstrapProject(
            @PathVariable Long projectId
    ) {
        projectBootstrapService.bootstrapReactProject(projectId);
        return ResponseEntity.ok().build();
    }


    @PostMapping("/{buildId}/validate-functional")
    public ResponseEntity<FullStackFunctionalValidationService.ValidationReport>
    validateFunctionalBuild(
            @PathVariable Long projectId,
            @PathVariable Long buildId
    ) throws Exception {

        // Verify that the authenticated user owns this project.
        var project = projectService.getProject(projectId);

        // Only FULL_STACK_APP projects support functional validation.
        if (project.getType() != ProjectType.FULL_STACK_APP) {
            return ResponseEntity.badRequest().build();
        }

        // Fetch the existing build without creating a new build.
        BuildRun build = buildService.getBuild(buildId);

        // Verify that the build belongs to this project.
        if (build.getProject() == null
                || !projectId.equals(build.getProject().getId())) {
            return ResponseEntity.notFound().build();
        }

        // The existing build must be associated with an agent run.
        if (build.getAgentRun() == null) {
            return ResponseEntity.badRequest().build();
        }

        Long agentRunId = build.getAgentRun().getId();

        var report = fullStackBuildValidationService.validate(
                projectId,
                agentRunId,
                buildId
        );

        return ResponseEntity.ok(report);
    }

}