package com.aibuilder.version.repository;

import com.aibuilder.version.entity.ProjectVersionFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectVersionFileRepository
        extends JpaRepository<ProjectVersionFile, Long> {

    List<ProjectVersionFile> findByVersionIdOrderByPathAsc(
            Long versionId
    );
}