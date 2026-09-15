package com.aibuilder.agent.repository;

import com.aibuilder.agent.entity.AgentTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentTaskRepository
        extends JpaRepository<AgentTask, Long> {

    List<AgentTask> findByAgentRunIdOrderByTaskOrderAsc(Long agentRunId);
}