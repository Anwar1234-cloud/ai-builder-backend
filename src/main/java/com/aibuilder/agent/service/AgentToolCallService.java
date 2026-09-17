package com.aibuilder.agent.service;

import com.aibuilder.agent.entity.AgentRun;
import com.aibuilder.agent.entity.AgentTask;
import com.aibuilder.agent.entity.AgentToolCall;
import com.aibuilder.agent.entity.AgentToolCallStatus;
import com.aibuilder.agent.repository.AgentRunRepository;
import com.aibuilder.agent.repository.AgentTaskRepository;
import com.aibuilder.agent.repository.AgentToolCallRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentToolCallService {

    private final AgentToolCallRepository agentToolCallRepository;
    private final AgentRunRepository agentRunRepository;
    private final AgentTaskRepository agentTaskRepository;


    @Transactional
    public Long startToolCall(
            Long agentRunId,
            Long agentTaskId,
            String toolName,
            String targetPath
    ) {

        AgentRun agentRun =
                agentRunRepository.findById(agentRunId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Agent run not found: " + agentRunId
                                )
                        );

        AgentTask agentTask = null;

        if (agentTaskId != null) {
            agentTask =
                    agentTaskRepository.findById(agentTaskId)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Agent task not found: "
                                                    + agentTaskId
                                    )
                            );


            if (!agentTask.getAgentRun().getId()
                    .equals(agentRunId)) {

                throw new RuntimeException(
                        "Agent task does not belong to the agent run"
                );
            }
        }

        AgentToolCall toolCall =
                new AgentToolCall();

        toolCall.setAgentRun(agentRun);
        toolCall.setAgentTask(agentTask);
        toolCall.setToolName(toolName);
        toolCall.setTargetPath(targetPath);
        toolCall.setStatus(
                AgentToolCallStatus.RUNNING
        );
        toolCall.setStartedAt(
                LocalDateTime.now()
        );

        toolCall =
                agentToolCallRepository.save(toolCall);

        return toolCall.getId();
    }


    @Transactional
    public Long startToolCall(
            Long agentRunId,
            String toolName,
            String targetPath
    ) {

        return startToolCall(
                agentRunId,
                null,
                toolName,
                targetPath
        );
    }


    @Transactional
    public void completeToolCall(Long toolCallId) {

        AgentToolCall toolCall =
                getToolCall(toolCallId);

        toolCall.setStatus(
                AgentToolCallStatus.COMPLETED
        );

        toolCall.setCompletedAt(
                LocalDateTime.now()
        );

        toolCall.setErrorMessage(null);

        agentToolCallRepository.save(toolCall);
    }


    @Transactional
    public void failToolCall(
            Long toolCallId,
            String errorMessage
    ) {

        AgentToolCall toolCall =
                getToolCall(toolCallId);

        toolCall.setStatus(
                AgentToolCallStatus.FAILED
        );

        toolCall.setCompletedAt(
                LocalDateTime.now()
        );

        toolCall.setErrorMessage(
                errorMessage
        );

        agentToolCallRepository.save(toolCall);
    }


    @Transactional(readOnly = true)
    public AgentToolCall getToolCall(Long toolCallId) {

        return agentToolCallRepository
                .findById(toolCallId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Tool call not found: "
                                        + toolCallId
                        )
                );
    }


    @Transactional(readOnly = true)
    public List<AgentToolCall> getToolCalls(
            Long agentRunId
    ) {

        return agentToolCallRepository
                .findByAgentRunIdOrderByStartedAtAsc(
                        agentRunId
                );
    }


    @Transactional(readOnly = true)
    public List<AgentToolCall> getToolCallsForTask(
            Long agentTaskId
    ) {

        return agentToolCallRepository
                .findByAgentTaskIdOrderByStartedAtAsc(
                        agentTaskId
                );
    }
}