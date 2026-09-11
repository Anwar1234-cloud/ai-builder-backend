package com.aibuilder.agent.repository;

import com.aibuilder.agent.entity.AgentRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentRunRepository
        extends JpaRepository<AgentRun, Long> {

    List<AgentRun> findByProjectIdOrderByStartedAtDesc(
            Long projectId
    );

    List<AgentRun> findByConversationIdOrderByStartedAtDesc(
            Long conversationId
    );

    Optional<AgentRun> findByIdAndProjectId(
            Long id,
            Long projectId
    );
}