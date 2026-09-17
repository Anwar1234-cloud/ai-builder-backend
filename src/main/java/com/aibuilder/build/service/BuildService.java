package com.aibuilder.build.service;

import com.aibuilder.agent.entity.AgentRun;
import com.aibuilder.agent.repository.AgentRunRepository;
import com.aibuilder.build.entity.BuildRun;
import com.aibuilder.build.entity.BuildStatus;
import com.aibuilder.build.repository.BuildRunRepository;
import com.aibuilder.project.entity.Project;
import com.aibuilder.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

            StringBuilder completeOutput =
                    new StringBuilder();


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
}