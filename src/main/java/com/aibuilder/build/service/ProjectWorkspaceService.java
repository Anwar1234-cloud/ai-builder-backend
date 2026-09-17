package com.aibuilder.build.service;

import com.aibuilder.project.entity.Project;
import com.aibuilder.project.repository.ProjectRepository;
import com.aibuilder.user.entity.User;
import com.aibuilder.user.repository.UserRepository;
import com.aibuilder.workspace.entity.ProjectFile;
import com.aibuilder.workspace.repository.ProjectFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectWorkspaceService {

    private final ProjectRepository projectRepository;
    private final ProjectFileRepository projectFileRepository;
    private final UserRepository userRepository;

    private final Path workspaceRoot =
            Paths.get("runtime", "workspaces")
                    .toAbsolutePath()
                    .normalize();


    public Path materializeProject(Long projectId, Long buildId) {

        User user = getAuthenticatedUser();

        Project project = getOwnedProject(
                projectId,
                user
        );

        try {
            Files.createDirectories(workspaceRoot);

            Path workspace =
                    workspaceRoot
                            .resolve(
                                    "project-"
                                            + projectId
                                            + "-build-"
                                            + buildId
                            )
                            .normalize();


            if (!workspace.startsWith(workspaceRoot)) {
                throw new SecurityException(
                        "Invalid workspace path"
                );
            }

            Files.createDirectories(workspace);

            List<ProjectFile> files =
                    projectFileRepository
                            .findByProjectIdOrderByPathAsc(
                                    projectId
                            );

            for (ProjectFile projectFile : files) {

                Path target =
                        workspace
                                .resolve(projectFile.getPath())
                                .normalize();

                if (!target.startsWith(workspace)) {
                    throw new SecurityException(
                            "Invalid project file path: "
                                    + projectFile.getPath()
                    );
                }

                Path parent = target.getParent();

                if (parent != null) {
                    Files.createDirectories(parent);
                }

                Files.writeString(
                        target,
                        projectFile.getContent(),
                        StandardCharsets.UTF_8
                );
            }

            return workspace;

        } catch (IOException e) {

            throw new RuntimeException(
                    "Failed to materialize project workspace",
                    e
            );
        }
    }


    public void deleteWorkspace(Path workspace) {

        if (workspace == null) {
            return;
        }

        Path normalized =
                workspace.toAbsolutePath()
                        .normalize();

        if (!normalized.startsWith(workspaceRoot)) {
            throw new SecurityException(
                    "Invalid workspace path"
            );
        }

        try {

            if (!Files.exists(normalized)) {
                return;
            }

            try (var stream =
                         Files.walk(normalized)) {

                stream
                        .sorted(
                                java.util.Comparator
                                        .reverseOrder()
                        )
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                throw new RuntimeException(
                                        "Failed to delete workspace: "
                                                + path,
                                        e
                                );
                            }
                        });
            }

        } catch (IOException e) {

            throw new RuntimeException(
                    "Failed to delete workspace",
                    e
            );
        }
    }

    private User getAuthenticatedUser() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated()) {

            throw new RuntimeException(
                    "User is not authenticated"
            );
        }

        return userRepository
                .findByEmail(
                        authentication
                                .getName()
                                .toLowerCase()
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Authenticated user not found"
                        )
                );
    }

    private Project getOwnedProject(
            Long projectId,
            User user
    ) {

        Project project =
                projectRepository
                        .findById(projectId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Project not found: "
                                                + projectId
                                )
                        );

        if (!project.getUser()
                .getId()
                .equals(user.getId())) {

            throw new RuntimeException(
                    "You do not have access to this project"
            );
        }

        return project;
    }
}