package com.aibuilder.project.repository;

import com.aibuilder.project.entity.ProjectVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectVersionRepository
        extends JpaRepository<ProjectVersion, Long> {

    List<ProjectVersion> findByProjectIdOrderByVersionNumberDesc(
            Long projectId
    );

    Optional<ProjectVersion> findTopByProjectIdOrderByVersionNumberDesc(
            Long projectId
    );
}