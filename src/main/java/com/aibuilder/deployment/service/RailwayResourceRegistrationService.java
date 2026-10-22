
package com.aibuilder.deployment.service;

import com.aibuilder.deployment.entity.RailwayProjectResources;
import com.aibuilder.deployment.exception.DeploymentException;
import com.aibuilder.deployment.repository.RailwayProjectResourcesRepository;
import com.aibuilder.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RailwayResourceRegistrationService {

    private final RailwayProjectResourcesRepository resourcesRepository;
    private final ProjectRepository projectRepository;

    @Transactional
    public RailwayProjectResources register(
            Long projectId,
            String railwayProjectId,
            String environmentId,
            String backendServiceId,
            String postgresServiceId
    ) {

        if (projectId == null
                || isBlank(railwayProjectId)
                || isBlank(environmentId)
                || isBlank(backendServiceId)) {
            throw new DeploymentException(
                    "Project and Railway resource IDs are required."
            );
        }

        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new DeploymentException(
                        "AI Builder project not found: " + projectId
                ));

        var existing = resourcesRepository.findByProjectId(projectId);

        if (existing.isPresent()) {
            RailwayProjectResources saved = existing.get();

            if (!railwayProjectId.equals(saved.getRailwayProjectId())
                    || !environmentId.equals(saved.getRailwayEnvironmentId())
                    || !backendServiceId.equals(saved.getRailwayBackendServiceId())) {
                throw new DeploymentException(
                        "Different Railway resources are already registered."
                );
            }

            if (!isBlank(postgresServiceId)) {
                if (!isBlank(saved.getRailwayPostgresServiceId())
                        && !postgresServiceId.equals(
                        saved.getRailwayPostgresServiceId())) {
                    throw new DeploymentException(
                            "Different PostgreSQL service is already registered."
                    );
                }

                saved.setRailwayPostgresServiceId(postgresServiceId);
            }

            return resourcesRepository.save(saved);
        }

        RailwayProjectResources resources =
                RailwayProjectResources.builder()
                        .project(project)
                        .railwayProjectId(railwayProjectId)
                        .railwayEnvironmentId(environmentId)
                        .railwayBackendServiceId(backendServiceId)
                        .railwayPostgresServiceId(postgresServiceId)
                        .build();

        return resourcesRepository.save(resources);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
