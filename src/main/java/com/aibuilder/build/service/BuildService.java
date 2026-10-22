package com.aibuilder.build.service;

import com.aibuilder.agent.entity.AgentRun;
import com.aibuilder.agent.repository.AgentRunRepository;
import com.aibuilder.build.entity.BuildRun;
import com.aibuilder.build.entity.BuildStatus;
import com.aibuilder.build.entity.VisualValidationStatus;
import com.aibuilder.build.repository.BuildRunRepository;
import com.aibuilder.project.entity.Project;
import com.aibuilder.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.aibuilder.project.entity.ProjectType;
import com.aibuilder.fullstack.runtime.GeneratedAppDatabaseService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BuildService {

    private final BuildRunRepository buildRunRepository;
    private final ProjectRepository projectRepository;
    private final AgentRunRepository agentRunRepository;
    private final ProjectWorkspaceService projectWorkspaceService;
    private final BuildExecutorService buildExecutorService;
    private final ProjectIntegrityService projectIntegrityService;
    private final GeneratedAppDatabaseService generatedAppDatabaseService;

    @Transactional
    public BuildRun createBuild(
            Long projectId,
            Long agentRunId,
            String command
    ) {

        Project project =
                projectRepository.findById(projectId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Project not found: " + projectId
                                )
                        );

        AgentRun agentRun = null;

        if (agentRunId != null) {

            agentRun =
                    agentRunRepository.findById(agentRunId)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Agent run not found: "
                                                    + agentRunId
                                    )
                            );

            if (!agentRun.getProject().getId()
                    .equals(projectId)) {

                throw new RuntimeException(
                        "Agent run does not belong to the project"
                );
            }
        }

        BuildRun buildRun = new BuildRun();

        buildRun.setProject(project);
        buildRun.setAgentRun(agentRun);
        buildRun.setCommand(command);
        buildRun.setStatus(BuildStatus.QUEUED);

        return buildRunRepository.save(buildRun);
    }

    @Transactional
    public BuildRun startBuild(Long buildId) {

        BuildRun buildRun = getBuild(buildId);

        buildRun.setStatus(BuildStatus.RUNNING);
        buildRun.setStartedAt(LocalDateTime.now());

        return buildRunRepository.save(buildRun);
    }

    @Transactional
    public BuildRun executeBuild(Long buildId) {

        BuildRun buildRun = getBuild(buildId);

        try {

            Path workspace;


            if (buildRun.getWorkspacePath() == null
                    || buildRun.getWorkspacePath().isBlank()) {

                workspace =
                        projectWorkspaceService.materializeProject(
                                buildRun.getProject().getId(),
                                buildRun.getId()
                        );

                buildRun.setWorkspacePath(
                        workspace.toString()
                );

            } else {

                workspace =
                        Path.of(
                                buildRun.getWorkspacePath()
                        );

                if (!Files.exists(workspace)) {

                    workspace =
                            projectWorkspaceService
                                    .materializeProject(
                                            buildRun.getProject().getId(),
                                            buildRun.getId()
                                    );

                    buildRun.setWorkspacePath(
                            workspace.toString()
                    );
                }
            }

            buildRun.setStatus(
                    BuildStatus.RUNNING
            );

            buildRun.setStartedAt(
                    LocalDateTime.now()
            );

            buildRunRepository.save(buildRun);

            if (buildRun.getProject().getType()
                    == ProjectType.FULL_STACK_APP) {

                return executeFullStackBuild(buildRun, workspace);
            }

            StringBuilder completeOutput =
                    new StringBuilder();

            ProjectIntegrityService.ValidationResult integrityResult =
                    projectIntegrityService.validate(
                            workspace
                    );


            if (!integrityResult.warnings().isEmpty()) {

                completeOutput
                        .append(
                                "===== PROJECT INTEGRITY WARNINGS =====\n"
                        )
                        .append(
                                integrityResult.formattedWarnings()
                        )
                        .append("\n\n");
            }


            if (!integrityResult.valid()) {

                completeOutput
                        .append(
                                "===== PROJECT INTEGRITY FAILED =====\n"
                        )
                        .append(
                                integrityResult.formattedErrors()
                        )
                        .append("\n");


                buildRun.setStatus(
                        BuildStatus.FAILED
                );

                buildRun.setOutput(
                        completeOutput.toString()
                );

                buildRun.setErrorOutput(
                        integrityResult.formattedErrors()
                );

                buildRun.setCompletedAt(
                        LocalDateTime.now()
                );


                return buildRunRepository.save(
                        buildRun
                );
            }


            completeOutput.append(
                    "===== PROJECT INTEGRITY PASSED =====\n\n"
            );


            if (Files.exists(
                    workspace.resolve("package.json")
            )) {

                BuildExecutorService.ExecutionResult installResult =
                        buildExecutorService.execute(
                                workspace,
                                "npm install"
                        );

                completeOutput
                        .append("===== npm install =====\n")
                        .append(installResult.output())
                        .append("\n");

                if (!installResult.isSuccess()) {

                    buildRun.setStatus(
                            BuildStatus.FAILED
                    );

                    buildRun.setOutput(
                            completeOutput.toString()
                    );

                    buildRun.setErrorOutput(
                            installResult.errorOutput()
                    );

                    buildRun.setCompletedAt(
                            LocalDateTime.now()
                    );

                    return buildRunRepository.save(
                            buildRun
                    );
                }
            }


            BuildExecutorService.ExecutionResult buildResult =
                    buildExecutorService.execute(
                            workspace,
                            buildRun.getCommand()
                    );

            completeOutput
                    .append("===== ")
                    .append(buildRun.getCommand())
                    .append(" =====\n")
                    .append(buildResult.output())
                    .append("\n");

            buildRun.setOutput(
                    completeOutput.toString()
            );

            buildRun.setErrorOutput(
                    buildResult.errorOutput()
            );

            buildRun.setCompletedAt(
                    LocalDateTime.now()
            );

            if (buildResult.isSuccess()) {

                buildRun.setStatus(
                        BuildStatus.SUCCESS
                );

            } else {

                buildRun.setStatus(
                        BuildStatus.FAILED
                );
            }

            return buildRunRepository.save(
                    buildRun
            );

        } catch (Exception e) {

            buildRun.setStatus(
                    BuildStatus.FAILED
            );

            buildRun.setErrorOutput(
                    e.getMessage()
            );

            buildRun.setCompletedAt(
                    LocalDateTime.now()
            );

            return buildRunRepository.save(
                    buildRun
            );
        }
    }



    private BuildRun executeFullStackBuild(
            BuildRun buildRun,
            Path workspace
    ) {

        StringBuilder output = new StringBuilder();
        StringBuilder errors = new StringBuilder();

        try {

            // 1. Validate full-stack structure
            ProjectIntegrityService.ValidationResult integrity =
                    projectIntegrityService.validateFullStack(workspace);

            if (!integrity.warnings().isEmpty()) {
                output.append("===== INTEGRITY WARNINGS =====\n")
                        .append(integrity.formattedWarnings())
                        .append("\n\n");
            }

            if (!integrity.valid()) {
                output.append("===== INTEGRITY FAILED =====\n")
                        .append(integrity.formattedErrors())
                        .append("\n");

                errors.append(integrity.formattedErrors());

                return finishFullStackBuild(
                        buildRun, false, output, errors
                );
            }

            output.append("===== INTEGRITY PASSED =====\n\n");

            Path frontend = workspace.resolve("frontend");
            Path backend = workspace.resolve("backend");

            // 2. Install frontend dependencies
            BuildExecutorService.ExecutionResult install =
                    buildExecutorService.execute(
                            frontend,
                            "npm install"
                    );

            appendExecution(
                    output,
                    errors,
                    "frontend: npm install",
                    install
            );

            if (!install.isSuccess()) {
                return finishFullStackBuild(
                        buildRun, false, output, errors
                );
            }

            // 3. Build React frontend
            BuildExecutorService.ExecutionResult frontendBuild =
                    buildExecutorService.execute(
                            frontend,
                            "npm run build"
                    );

            appendExecution(
                    output,
                    errors,
                    "frontend: npm run build",
                    frontendBuild
            );

            if (!frontendBuild.isSuccess()) {
                return finishFullStackBuild(
                        buildRun, false, output, errors
                );
            }

            // 4. Package executable Spring Boot backend
            BuildExecutorService.ExecutionResult backendBuild =
                    buildExecutorService.execute(
                            backend,
                            "mvn package -DskipTests"
                    );

            appendExecution(
                    output,
                    errors,
                    "backend: mvn package -DskipTests",
                    backendBuild
            );

            if (!backendBuild.isSuccess()) {
                return finishFullStackBuild(
                        buildRun, false, output, errors
                );
            }

            output.append(
                    "\n===== FRONTEND AND BACKEND BUILDS PASSED =====\n"
            );

            // 5. Provision generated application database
            try {

                String databaseName =
                        generatedAppDatabaseService.provisionDatabase(
                                buildRun.getProject().getId()
                        );

                output.append(
                                "\n===== DATABASE PROVISIONING =====\n"
                        )
                        .append("Database ready: ")
                        .append(databaseName)
                        .append("\n");

            } catch (Exception exception) {

                errors.append(
                        "Database provisioning failed: "
                                + exception.getMessage()
                );

                return finishFullStackBuild(
                        buildRun, false, output, errors
                );
            }

            // 6. All build stages completed successfully
            return finishFullStackBuild(
                    buildRun, true, output, errors
            );

        } catch (Exception exception) {

            errors.append(
                    "Full-stack build failed: "
                            + exception.getMessage()
            );

            return finishFullStackBuild(
                    buildRun, false, output, errors
            );
        }
    }


    private void appendExecution(
            StringBuilder output,
            StringBuilder errors,
            String title,
            BuildExecutorService.ExecutionResult result
    ) {

        output.append("\n===== ")
                .append(title)
                .append(" =====\n")
                .append(result.output())
                .append("\n");

        if (result.errorOutput() != null
                && !result.errorOutput().isBlank()) {

            errors.append("\n===== ")
                    .append(title)
                    .append(" =====\n")
                    .append(result.errorOutput())
                    .append("\n");
        }

        if (!result.isSuccess()) {
            errors.append(title)
                    .append(" failed with exit code ")
                    .append(result.exitCode())
                    .append("\n");
        }
    }

    private BuildRun finishFullStackBuild(
            BuildRun buildRun,
            boolean success,
            StringBuilder output,
            StringBuilder errors
    ) {

        buildRun.setStatus(
                success ? BuildStatus.SUCCESS : BuildStatus.FAILED
        );

        buildRun.setOutput(output.toString());
        buildRun.setErrorOutput(errors.toString());
        buildRun.setCompletedAt(LocalDateTime.now());

        return buildRunRepository.save(buildRun);
    }


    @Transactional
    public BuildRun materializeBuildWorkspace(
            Long buildId
    ) {

        BuildRun buildRun = getBuild(buildId);

        Path workspace =
                projectWorkspaceService.materializeProject(
                        buildRun.getProject().getId(),
                        buildRun.getId()
                );

        buildRun.setWorkspacePath(
                workspace.toString()
        );

        return buildRunRepository.save(
                buildRun
        );
    }

    @Transactional
    public BuildRun completeBuild(
            Long buildId,
            String output
    ) {

        BuildRun buildRun = getBuild(buildId);

        buildRun.setStatus(
                BuildStatus.SUCCESS
        );

        buildRun.setOutput(output);
        buildRun.setCompletedAt(
                LocalDateTime.now()
        );

        return buildRunRepository.save(
                buildRun
        );
    }

    @Transactional
    public BuildRun failBuild(
            Long buildId,
            String output,
            String errorOutput
    ) {

        BuildRun buildRun = getBuild(buildId);

        buildRun.setStatus(
                BuildStatus.FAILED
        );

        buildRun.setOutput(output);
        buildRun.setErrorOutput(errorOutput);
        buildRun.setCompletedAt(
                LocalDateTime.now()
        );

        return buildRunRepository.save(
                buildRun
        );
    }

    @Transactional(readOnly = true)
    public BuildRun getBuild(Long buildId) {

        return buildRunRepository.findById(buildId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Build run not found: "
                                        + buildId
                        )
                );
    }

    @Transactional(readOnly = true)
    public List<BuildRun> getBuilds(
            Long projectId
    ) {

        projectRepository.findById(projectId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Project not found: "
                                        + projectId
                        )
                );

        return buildRunRepository
                .findByProjectIdOrderByStartedAtDesc(
                        projectId
                );
    }

    @Transactional(readOnly = true)
    public List<BuildRun> getBuildsForAgentRun(
            Long agentRunId
    ) {

        agentRunRepository.findById(agentRunId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Agent run not found: "
                                        + agentRunId
                        )
                );

        return buildRunRepository
                .findByAgentRunIdOrderByStartedAtDesc(
                        agentRunId
                );
    }
    @Transactional
    public BuildRun markVisualValidationPassed(
            Long buildId
    ) {

        BuildRun buildRun =
                buildRunRepository
                        .findById(buildId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Build not found: "
                                                + buildId
                                )
                        );


        if (buildRun.getStatus()
                != BuildStatus.SUCCESS) {

            throw new RuntimeException(
                    "Only a successful build can pass visual validation."
            );
        }


        buildRun.setVisualValidationStatus(
                VisualValidationStatus.PASSED
        );


        return buildRunRepository.save(
                buildRun
        );
    }


    @Transactional
    public BuildRun markVisualValidationFailed(
            Long buildId
    ) {

        BuildRun buildRun =
                buildRunRepository
                        .findById(buildId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Build not found: "
                                                + buildId
                                )
                        );


        buildRun.setVisualValidationStatus(
                VisualValidationStatus.FAILED
        );


        return buildRunRepository.save(
                buildRun
        );
    }
}