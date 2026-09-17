package com.aibuilder.build.repository;

import com.aibuilder.build.entity.BuildRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BuildRunRepository
        extends JpaRepository<BuildRun, Long> {

    List<BuildRun>
    findByProjectIdOrderByStartedAtDesc(Long projectId);

    List<BuildRun>
    findByAgentRunIdOrderByStartedAtDesc(Long agentRunId);
}