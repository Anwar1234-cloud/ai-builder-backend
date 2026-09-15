package com.aibuilder.version.service;

import com.aibuilder.project.entity.Project;
import com.aibuilder.project.repository.ProjectRepository;
import com.aibuilder.user.entity.User;
import com.aibuilder.user.repository.UserRepository;
import com.aibuilder.version.entity.ProjectVersion;
import com.aibuilder.version.entity.ProjectVersionFile;
import com.aibuilder.version.repository.ProjectVersionFileRepository;
import com.aibuilder.version.repository.ProjectVersionRepository;
import com.aibuilder.workspace.entity.ProjectFile;
import com.aibuilder.workspace.repository.ProjectFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectVersionService {

    private final ProjectRepository projectRepository;
    private final ProjectVersionRepository projectVersionRepository;
    private final ProjectVersionFileRepository projectVersionFileRepository;
    private final ProjectFileRepository projectFileRepository;
    private final UserRepository userRepository;


    /**
     * Creates a project version containing only version metadata.
     */
    @Transactional
    public ProjectVersion createVersion(
            Long projectId,
            String message,
            String source
    ) {

        User user = getAuthenticatedUser();

        Project project = getOwnedProject(projectId, user);

        Integer latestVersion = projectVersionRepository
                .findTopByProjectIdOrderByVersionNumberDesc(projectId)
                .map(ProjectVersion::getVersionNumber)
                .orElse(0);

        ProjectVersion version = new ProjectVersion();

        version.setProject(project);
        version.setVersionNumber(latestVersion + 1);
        version.setMessage(message);
        version.setSource(source);

        return projectVersionRepository.save(version);
    }


    /**
     * Creates a complete snapshot of the current project files.
     */
    @Transactional
    public ProjectVersion createSnapshot(
            Long projectId,
            String message,
            String source
    ) {

        User user = getAuthenticatedUser();

        Project project = getOwnedProject(projectId, user);

        Integer latestVersion = projectVersionRepository
                .findTopByProjectIdOrderByVersionNumberDesc(projectId)
                .map(ProjectVersion::getVersionNumber)
                .orElse(0);

        ProjectVersion version = new ProjectVersion();

        version.setProject(project);
        version.setVersionNumber(latestVersion + 1);
        version.setMessage(message);
        version.setSource(source);

        version = projectVersionRepository.save(version);

        // Get current project files
        List<ProjectFile> projectFiles =
                projectFileRepository
                        .findByProjectIdOrderByPathAsc(projectId);

        // Store a copy of every current file
        for (ProjectFile projectFile : projectFiles) {

            ProjectVersionFile snapshotFile =
                    new ProjectVersionFile();

            snapshotFile.setVersion(version);
            snapshotFile.setPath(projectFile.getPath());
            snapshotFile.setContent(projectFile.getContent());
            snapshotFile.setLanguage(projectFile.getLanguage());

            projectVersionFileRepository.save(snapshotFile);
        }

        return version;
    }


    /**
     * Restores the project to a previous version.
     *
     * The old version remains unchanged.
     * After restoring, a NEW version is created with source RESTORE.
     */
    @Transactional
    public ProjectVersion restoreVersion(
            Long projectId,
            Integer versionNumber
    ) {

        User user = getAuthenticatedUser();

        Project project = getOwnedProject(projectId, user);

        // Find the version that should be restored
        ProjectVersion versionToRestore =
                projectVersionRepository
                        .findByProjectIdAndVersionNumber(
                                projectId,
                                versionNumber
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Version not found: " + versionNumber
                                )
                        );

        // Get files stored inside that snapshot
        List<ProjectVersionFile> snapshotFiles =
                projectVersionFileRepository
                        .findByVersionIdOrderByPathAsc(
                                versionToRestore.getId()
                        );

        // Get current project files
        List<ProjectFile> currentFiles =
                projectFileRepository
                        .findByProjectIdOrderByPathAsc(projectId);

        // Delete current project files
        projectFileRepository.deleteAll(currentFiles);

        /*
         * Recreate the project files from the selected snapshot.
         */
        for (ProjectVersionFile snapshotFile : snapshotFiles) {

            ProjectFile projectFile = new ProjectFile();

            projectFile.setProject(project);
            projectFile.setPath(snapshotFile.getPath());
            projectFile.setContent(snapshotFile.getContent());
            projectFile.setLanguage(snapshotFile.getLanguage());

            projectFileRepository.save(projectFile);
        }

        /*
         * Create a NEW version representing the restore operation.
         *
         * Example:
         * Version 3 restored -> Version 4 created with source RESTORE.
         */
        return createSnapshot(
                projectId,
                "Restored version " + versionNumber,
                "RESTORE"
        );
    }


    /**
     * Gets all versions of a project.
     */
    @Transactional(readOnly = true)
    public List<ProjectVersion> getVersions(Long projectId) {

        User user = getAuthenticatedUser();

        getOwnedProject(projectId, user);

        return projectVersionRepository
                .findByProjectIdOrderByVersionNumberDesc(projectId);
    }


    /**
     * Gets one specific version.
     */
    @Transactional(readOnly = true)
    public ProjectVersion getVersion(
            Long projectId,
            Integer versionNumber
    ) {

        User user = getAuthenticatedUser();

        getOwnedProject(projectId, user);

        return projectVersionRepository
                .findByProjectIdAndVersionNumber(
                        projectId,
                        versionNumber
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Version not found: " + versionNumber
                        )
                );
    }


    /**
     * Gets all files stored inside a specific version snapshot.
     */
    @Transactional(readOnly = true)
    public List<ProjectVersionFile> getSnapshotFiles(
            Long projectId,
            Integer versionNumber
    ) {

        // getVersion() already verifies project ownership
        ProjectVersion version =
                getVersion(projectId, versionNumber);

        return projectVersionFileRepository
                .findByVersionIdOrderByPathAsc(
                        version.getId()
                );
    }


    /**
     * Gets the currently authenticated user.
     */
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


    /**
     * Gets a project only if the authenticated user owns it.
     */
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
}