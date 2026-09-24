package com.aibuilder.project.service;

import com.aibuilder.build.service.ProjectBootstrapService;
import com.aibuilder.project.dto.CreateProjectRequest;
import com.aibuilder.project.dto.ProjectResponse;
import com.aibuilder.project.entity.Project;
import com.aibuilder.project.entity.ProjectType;
import com.aibuilder.project.repository.ProjectRepository;
import com.aibuilder.user.entity.User;
import com.aibuilder.user.repository.UserRepository;
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
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectBootstrapService projectBootstrapService;

    public ProjectResponse createProject(CreateProjectRequest request) {

        User user = getAuthenticatedUser();

        Project project = Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type(request.getType())
                .user(user)
                .build();

        Project savedProject = projectRepository.save(project);

        if (savedProject.getType() == ProjectType.WEBSITE
                || savedProject.getType() == ProjectType.WEB_APP
                || savedProject.getType() == ProjectType.FULL_STACK_APP) {

            projectBootstrapService.bootstrapReactProject(
                    savedProject.getId()
            );
        }

        return toResponse(savedProject);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(Long id) {

        User user = getAuthenticatedUser();

        Project project = projectRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Project not found")
                );

        if (!project.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You do not have access to this project");
        }

        return toResponse(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getMyProjects() {

        User user = getAuthenticatedUser();

        return projectRepository.findByUserId(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ProjectResponse updateProject(
            Long id,
            CreateProjectRequest request
    ) {

        User user = getAuthenticatedUser();

        Project project = projectRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Project not found")
                );

        if (!project.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You do not have access to this project");
        }

        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setType(request.getType());
        project.setUpdatedAt(LocalDateTime.now());

        return toResponse(project);
    }

    public void deleteProject(Long id) {

        User user = getAuthenticatedUser();

        Project project = projectRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Project not found")
                );

        if (!project.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You do not have access to this project");
        }

        projectRepository.delete(project);
    }

    private User getAuthenticatedUser() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated()) {

            throw new RuntimeException("User is not authenticated");
        }

        String email = authentication.getName();

        return userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() ->
                        new RuntimeException("Authenticated user not found")
                );
    }

    private ProjectResponse toResponse(Project project) {

        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .type(project.getType())
                .userId(project.getUser().getId())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}