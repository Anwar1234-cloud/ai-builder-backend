package com.aibuilder.workspace.service;

import com.aibuilder.project.entity.Project;
import com.aibuilder.project.repository.ProjectRepository;
import com.aibuilder.user.entity.User;
import com.aibuilder.user.repository.UserRepository;
import com.aibuilder.workspace.dto.CreateProjectFileRequest;
import com.aibuilder.workspace.dto.ProjectFileResponse;
import com.aibuilder.workspace.entity.ProjectFile;
import com.aibuilder.workspace.repository.ProjectFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkspaceService {

    private final ProjectFileRepository projectFileRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ProjectFileResponse createFile(
            Long projectId,
            CreateProjectFileRequest request
    ) {

        User user = getAuthenticatedUser();

        Project project = getOwnedProject(projectId, user);

        if (projectFileRepository.existsByProjectIdAndPath(
                projectId,
                request.getPath()
        )) {
            throw new RuntimeException("File already exists");
        }

        ProjectFile file = ProjectFile.builder()
                .project(project)
                .path(request.getPath())
                .content(request.getContent())
                .language(
                        request.getLanguage() == null
                                ? "text"
                                : request.getLanguage()
                )
                .build();

        return toResponse(
                projectFileRepository.save(file)
        );
    }

    @Transactional(readOnly = true)
    public List<ProjectFileResponse> getFiles(Long projectId) {

        User user = getAuthenticatedUser();

        getOwnedProject(projectId, user);

        return projectFileRepository
                .findByProjectIdOrderByPathAsc(projectId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectFileResponse getFile(
            Long projectId,
            String path
    ) {

        User user = getAuthenticatedUser();

        getOwnedProject(projectId, user);

        ProjectFile file =
                projectFileRepository
                        .findByProjectIdAndPath(projectId, path)
                        .orElseThrow(() ->
                                new RuntimeException("File not found")
                        );

        return toResponse(file);
    }

    public ProjectFileResponse updateFile(
            Long projectId,
            String path,
            CreateProjectFileRequest request
    ) {

        User user = getAuthenticatedUser();

        getOwnedProject(projectId, user);

        ProjectFile file =
                projectFileRepository
                        .findByProjectIdAndPath(projectId, path)
                        .orElseThrow(() ->
                                new RuntimeException("File not found")
                        );

        file.setContent(request.getContent());

        if (request.getLanguage() != null) {
            file.setLanguage(request.getLanguage());
        }

        file.setUpdatedAt(LocalDateTime.now());

        return toResponse(file);
    }

    public void deleteFile(
            Long projectId,
            String path
    ) {

        User user = getAuthenticatedUser();

        getOwnedProject(projectId, user);

        ProjectFile file =
                projectFileRepository
                        .findByProjectIdAndPath(projectId, path)
                        .orElseThrow(() ->
                                new RuntimeException("File not found")
                        );

        projectFileRepository.delete(file);
    }

    private Project getOwnedProject(
            Long projectId,
            User user
    ) {

        Project project =
                projectRepository.findById(projectId)
                        .orElseThrow(() ->
                                new RuntimeException("Project not found")
                        );

        if (!project.getUser().getId().equals(user.getId())) {
            throw new RuntimeException(
                    "You do not have access to this project"
            );
        }

        return project;
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
                        authentication.getName().toLowerCase()
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Authenticated user not found"
                        )
                );
    }

    private ProjectFileResponse toResponse(
            ProjectFile file
    ) {

        return ProjectFileResponse.builder()
                .id(file.getId())
                .projectId(file.getProject().getId())
                .path(file.getPath())
                .content(file.getContent())
                .language(file.getLanguage())
                .createdAt(file.getCreatedAt())
                .updatedAt(file.getUpdatedAt())
                .build();
    }
}