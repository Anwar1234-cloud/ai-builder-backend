package com.aibuilder.workspace.repository;

import com.aibuilder.workspace.entity.ProjectFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectFileRepository
        extends JpaRepository<ProjectFile, Long> {

    List<ProjectFile> findByProjectIdOrderByPathAsc(Long projectId);

    Optional<ProjectFile> findByProjectIdAndPath(
            Long projectId,
            String path
    );

    boolean existsByProjectIdAndPath(
            Long projectId,
            String path
    );
}