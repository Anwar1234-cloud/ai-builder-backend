package com.aibuilder.conversation.dto;

import com.aibuilder.conversation.entity.MessageRole;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MessageResponse {

    private Long id;
    private Long conversationId;
    private MessageRole role;
    private String content;
    private LocalDateTime createdAt;
}