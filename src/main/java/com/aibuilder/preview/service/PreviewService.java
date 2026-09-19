package com.aibuilder.preview.service;

import com.aibuilder.build.entity.BuildRun;
import com.aibuilder.build.entity.BuildStatus;
import com.aibuilder.build.repository.BuildRunRepository;
import com.aibuilder.project.entity.Project;
import com.aibuilder.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class PreviewService {

    private final BuildRunRepository buildRunRepository;
    private final ProjectRepository projectRepository;

    public Resource getPreviewFile(
            Long projectId,
            Long buildId,
            String requestedPath
    ) {

        Project project =
                projectRepository.findById(projectId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Project not found: " + projectId
                                )
                        );

        BuildRun buildRun =
                buildRunRepository.findById(buildId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Build not found: " + buildId
                                )
                        );

        if (!buildRun.getProject().getId()
                .equals(project.getId())) {

            throw new RuntimeException(
                    "Build does not belong to this project"
            );
        }

        if (buildRun.getStatus() != BuildStatus.SUCCESS) {

            throw new RuntimeException(
                    "Only successful builds can be previewed"
            );
        }

        if (buildRun.getWorkspacePath() == null ||
                buildRun.getWorkspacePath().isBlank()) {

            throw new RuntimeException(
                    "Build workspace not found"
            );
        }

        Path workspace =
                Path.of(buildRun.getWorkspacePath())
                        .toAbsolutePath()
                        .normalize();

        Path distDirectory =
                workspace.resolve("dist")
                        .normalize();

        if (!distDirectory.startsWith(workspace)) {
            throw new SecurityException(
                    "Invalid preview path"
            );
        }

        String cleanPath =
                requestedPath == null ||
                        requestedPath.isBlank()
                        ? "index.html"
                        : requestedPath;

        if (cleanPath.startsWith("/")
                || cleanPath.startsWith("\\")
                || cleanPath.contains("..")) {

            throw new SecurityException(
                    "Invalid preview file path"
            );
        }

        Path requestedFile =
                distDirectory
                        .resolve(cleanPath)
                        .normalize();

        if (!requestedFile.startsWith(distDirectory)) {
            throw new SecurityException(
                    "Invalid preview file path"
            );
        }

        if (!Files.exists(requestedFile) ||
                !Files.isRegularFile(requestedFile)) {

            throw new RuntimeException(
                    "Preview file not found: " + cleanPath
            );
        }

        return new FileSystemResource(
                requestedFile.toFile()
        );
    }
}