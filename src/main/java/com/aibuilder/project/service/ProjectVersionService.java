package com.aibuilder.project.service;

import com.aibuilder.project.dto.ProjectVersionResponse;
import com.aibuilder.project.entity.Project;
import com.aibuilder.project.entity.ProjectVersion;
import com.aibuilder.project.repository.ProjectRepository;
import com.aibuilder.project.repository.ProjectVersionRepository;
import com.aibuilder.user.entity.User;
import com.aibuilder.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectVersionService {

    private final ProjectVersionRepository versionRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ProjectVersionResponse createVersion(
            Long projectId,
            String message,
            String source
    ) {

        User user = getAuthenticatedUser();

        Project project = getOwnedProject(projectId, user);

        Integer nextVersion =
                versionRepository
                        .findTopByProjectIdOrderByVersionNumberDesc(projectId)
                        .map(version -> version.getVersionNumber() + 1)
                        .orElse(1);

        ProjectVersion version = ProjectVersion.builder()
                .project(project)
                .versionNumber(nextVersion)
                .message(message)
                .source(source)
                .build();

        return toResponse(versionRepository.save(version));
    }

    @Transactional(readOnly = true)
    public List<ProjectVersionResponse> getVersions(
            Long projectId
    ) {

        User user = getAuthenticatedUser();

        getOwnedProject(projectId, user);

        return versionRepository
                .findByProjectIdOrderByVersionNumberDesc(projectId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private Project getOwnedProject(
            Long projectId,
            User user
    ) {

        Project project =
                projectRepository.findById(projectId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Project not found"
                                )
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

    private ProjectVersionResponse toResponse(
            ProjectVersion version
    ) {

        return ProjectVersionResponse.builder()
                .id(version.getId())
                .projectId(version.getProject().getId())
                .versionNumber(version.getVersionNumber())
                .message(version.getMessage())
                .source(version.getSource())
                .createdAt(version.getCreatedAt())
                .build();
    }
}