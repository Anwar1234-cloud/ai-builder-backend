
package com.aibuilder.fullstack.repository;

import com.aibuilder.fullstack.entity.FullStackArchitecture;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FullStackArchitectureRepository
        extends JpaRepository<FullStackArchitecture, Long> {

    Optional<FullStackArchitecture> findByAgentRunId(
            Long agentRunId
    );

    Optional<FullStackArchitecture>
    findFirstByProjectIdOrderByCreatedAtDesc(
            Long projectId
    );
}
