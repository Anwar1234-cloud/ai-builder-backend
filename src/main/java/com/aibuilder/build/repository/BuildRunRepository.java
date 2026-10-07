package com.aibuilder.build.repository;

import com.aibuilder.build.entity.BuildRun;
import com.aibuilder.build.entity.BuildStatus;
import com.aibuilder.build.entity.VisualValidationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BuildRunRepository
        extends JpaRepository<BuildRun, Long> {

    List<BuildRun>
    findByProjectIdOrderByStartedAtDesc(Long projectId);

    List<BuildRun>
    findByAgentRunIdOrderByStartedAtDesc(Long agentRunId);

    Optional<BuildRun>
    findFirstByProjectIdAndStatusAndVisualValidationStatusOrderByStartedAtDesc(
            Long projectId,
            BuildStatus status,
            VisualValidationStatus visualValidationStatus
    );
}