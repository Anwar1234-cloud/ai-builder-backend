package com.aibuilder.conversation.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ConversationResponse {

    private Long id;
    private Long projectId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}