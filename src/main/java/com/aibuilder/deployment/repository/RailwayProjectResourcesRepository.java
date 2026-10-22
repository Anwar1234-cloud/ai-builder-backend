
package com.aibuilder.deployment.repository;

import com.aibuilder.deployment.entity.RailwayProjectResources;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RailwayProjectResourcesRepository
        extends JpaRepository<RailwayProjectResources, Long> {

    Optional<RailwayProjectResources> findByProjectId(
            Long projectId
    );
}
