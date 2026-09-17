package com.aibuilder.agent.repository;

import com.aibuilder.agent.entity.AgentToolCall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentToolCallRepository
        extends JpaRepository<AgentToolCall, Long> {

    List<AgentToolCall>
    findByAgentRunIdOrderByStartedAtAsc(Long agentRunId);

    List<AgentToolCall>
    findByAgentTaskIdOrderByStartedAtAsc(Long agentTaskId);
}