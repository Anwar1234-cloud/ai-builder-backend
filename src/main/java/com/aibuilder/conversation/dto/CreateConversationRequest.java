package com.aibuilder.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateConversationRequest {

    @NotBlank(message = "Conversation title is required")
    @Size(
            max = 200,
            message = "Conversation title cannot exceed 200 characters"
    )
    private String title;
}