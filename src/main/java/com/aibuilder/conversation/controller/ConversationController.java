package com.aibuilder.conversation.controller;

import com.aibuilder.conversation.dto.ConversationResponse;
import com.aibuilder.conversation.dto.CreateConversationRequest;
import com.aibuilder.conversation.dto.CreateMessageRequest;
import com.aibuilder.conversation.dto.MessageResponse;
import com.aibuilder.conversation.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    public ResponseEntity<ConversationResponse> createConversation(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateConversationRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        conversationService.createConversation(
                                projectId,
                                request
                        )
                );
    }

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getConversations(
            @PathVariable Long projectId
    ) {

        return ResponseEntity.ok(
                conversationService.getConversations(projectId)
        );
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(
            @PathVariable Long projectId,
            @PathVariable Long conversationId
    ) {

        return ResponseEntity.ok(
                conversationService.getMessages(
                        projectId,
                        conversationId
                )
        );
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<MessageResponse> addUserMessage(
            @PathVariable Long projectId,
            @PathVariable Long conversationId,
            @Valid @RequestBody CreateMessageRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        conversationService.addUserMessage(
                                projectId,
                                conversationId,
                                request
                        )
                );
    }
}