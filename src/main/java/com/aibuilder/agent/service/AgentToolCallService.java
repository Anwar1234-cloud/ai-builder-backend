package com.aibuilder.agent.service;

import com.aibuilder.agent.entity.AgentRun;
import com.aibuilder.agent.entity.AgentToolCall;
import com.aibuilder.agent.entity.AgentToolCallStatus;
import com.aibuilder.agent.repository.AgentRunRepository;
import com.aibuilder.agent.repository.AgentToolCallRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentToolCallService {

    private final AgentRunRepository agentRunRepository;
    private final AgentToolCallRepository agentToolCallRepository;

    @Transactional
    public Long startToolCall(
            Long agentRunId,
            String toolName,
            String targetPath
    ) {

        AgentRun agentRun =
                agentRunRepository.findById(agentRunId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Agent run not found: " + agentRunId
                                )
                        );

        AgentToolCall toolCall = new AgentToolCall();

        toolCall.setAgentRun(agentRun);
        toolCall.setToolName(toolName);
        toolCall.setTargetPath(targetPath);
        toolCall.setStatus(AgentToolCallStatus.RUNNING);
        toolCall.setStartedAt(LocalDateTime.now());

        return agentToolCallRepository
                .save(toolCall)
                .getId();
    }

    @Transactional
    public void completeToolCall(Long toolCallId) {

        AgentToolCall toolCall =
                agentToolCallRepository.findById(toolCallId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Tool call not found: "
                                                + toolCallId
                                )
                        );

        toolCall.setStatus(AgentToolCallStatus.COMPLETED);
        toolCall.setCompletedAt(LocalDateTime.now());

        agentToolCallRepository.save(toolCall);
    }

    @Transactional
    public void failToolCall(
            Long toolCallId,
            String errorMessage
    ) {

        AgentToolCall toolCall =
                agentToolCallRepository.findById(toolCallId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Tool call not found: "
                                                + toolCallId
                                )
                        );

        toolCall.setStatus(AgentToolCallStatus.FAILED);
        toolCall.setCompletedAt(LocalDateTime.now());
        toolCall.setErrorMessage(errorMessage);

        agentToolCallRepository.save(toolCall);
    }

    @Transactional(readOnly = true)
    public List<AgentToolCall> getToolCalls(Long agentRunId) {

        return agentToolCallRepository
                .findByAgentRunIdOrderByStartedAtAsc(agentRunId);
    }
}