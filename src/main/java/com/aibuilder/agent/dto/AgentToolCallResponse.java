package com.aibuilder.agent.dto;

import com.aibuilder.agent.entity.AgentToolCallStatus;
import com.aibuilder.agent.entity.AgentToolCall;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AgentToolCallResponse {

    private Long id;
    private String toolName;
    private String targetPath;
    private AgentToolCallStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;

    public static AgentToolCallResponse from(
            AgentToolCall toolCall
    ) {

        return AgentToolCallResponse.builder()
                .id(toolCall.getId())
                .toolName(toolCall.getToolName())
                .targetPath(toolCall.getTargetPath())
                .status(toolCall.getStatus())
                .startedAt(toolCall.getStartedAt())
                .completedAt(toolCall.getCompletedAt())
                .errorMessage(toolCall.getErrorMessage())
                .build();
    }
}