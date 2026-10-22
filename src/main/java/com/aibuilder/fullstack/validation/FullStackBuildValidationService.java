
package com.aibuilder.fullstack.validation;

import com.aibuilder.build.entity.BuildRun;
import com.aibuilder.build.entity.BuildStatus;
import com.aibuilder.build.service.BuildService;
import com.aibuilder.fullstack.dto.FullStackPlan;
import com.aibuilder.fullstack.service.FullStackArchitectureService;
import com.aibuilder.project.entity.ProjectType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class FullStackBuildValidationService {

    private final BuildService buildService;

    private final FullStackArchitectureService architectureService;

    private final FullStackValidationOrchestrator orchestrator;

    @Transactional(readOnly = true)
    public FullStackFunctionalValidationService.ValidationReport validate(
            Long projectId,
            Long agentRunId,
            Long buildId
    ) throws Exception {

        BuildRun build = buildService.getBuild(buildId);

        if (!build.getProject().getId().equals(projectId)) {
            throw new IllegalArgumentException(
                    "Build does not belong to project"
            );
        }

        if (build.getProject().getType()
                != ProjectType.FULL_STACK_APP) {
            throw new IllegalArgumentException(
                    "Functional validation supports FULL_STACK_APP only"
            );
        }

        if (build.getAgentRun() == null
                || !build.getAgentRun().getId()
                .equals(agentRunId)) {
            throw new IllegalArgumentException(
                    "Build does not belong to agent run"
            );
        }

        if (build.getStatus() != BuildStatus.SUCCESS) {
            throw new IllegalStateException(
                    "Cannot validate an unsuccessful build"
            );
        }

        if (build.getWorkspacePath() == null
                || build.getWorkspacePath().isBlank()) {
            throw new IllegalStateException(
                    "Build workspace path is missing"
            );
        }

        Path workspace = Path.of(
                build.getWorkspacePath()
        ).toAbsolutePath().normalize();

        Path backend = workspace.resolve("backend").normalize();

        if (!backend.startsWith(workspace)
                || !Files.isDirectory(backend)
                || !Files.isRegularFile(
                backend.resolve("pom.xml"))) {
            throw new IllegalStateException(
                    "Generated backend workspace is invalid"
            );
        }

        FullStackPlan plan =
                architectureService.getPlan(agentRunId);

        if (plan == null) {
            throw new IllegalStateException(
                    "Saved full-stack architecture is missing"
            );
        }

        return orchestrator.validate(
                projectId,
                backend,
                plan
        );
    }


}
