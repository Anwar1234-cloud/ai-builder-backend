package com.aibuilder.agent.dto;

import com.aibuilder.agent.entity.AgentRun;
import com.aibuilder.agent.entity.AgentRunStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AgentRunResponse {

    private Long id;
    private Long projectId;
    private Long conversationId;
    private AgentRunStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;

    public static AgentRunResponse from(AgentRun run) {
        return AgentRunResponse.builder()
                .id(run.getId())
                .projectId(run.getProject().getId())
                .conversationId(run.getConversation().getId())
                .status(run.getStatus())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .errorMessage(run.getErrorMessage())
                .build();
    }
}