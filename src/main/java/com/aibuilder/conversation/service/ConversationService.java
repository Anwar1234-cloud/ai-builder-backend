package com.aibuilder.conversation.service;

import com.aibuilder.conversation.dto.ConversationResponse;
import com.aibuilder.conversation.dto.CreateConversationRequest;
import com.aibuilder.conversation.dto.CreateMessageRequest;
import com.aibuilder.conversation.dto.MessageResponse;
import com.aibuilder.conversation.entity.Conversation;
import com.aibuilder.conversation.entity.Message;
import com.aibuilder.conversation.entity.MessageRole;
import com.aibuilder.conversation.repository.ConversationRepository;
import com.aibuilder.conversation.repository.MessageRepository;
import com.aibuilder.project.entity.Project;
import com.aibuilder.project.repository.ProjectRepository;
import com.aibuilder.user.entity.User;
import com.aibuilder.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ConversationResponse createConversation(
            Long projectId,
            CreateConversationRequest request
    ) {

        User user = getAuthenticatedUser();

        Project project = getOwnedProject(projectId, user);

        Conversation conversation = Conversation.builder()
                .project(project)
                .title(request.getTitle())
                .build();

        return toConversationResponse(
                conversationRepository.save(conversation)
        );
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getConversations(
            Long projectId
    ) {

        User user = getAuthenticatedUser();

        getOwnedProject(projectId, user);

        return conversationRepository
                .findByProjectIdOrderByUpdatedAtDesc(projectId)
                .stream()
                .map(this::toConversationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(
            Long projectId,
            Long conversationId
    ) {

        User user = getAuthenticatedUser();

        Conversation conversation =
                getOwnedConversation(
                        projectId,
                        conversationId,
                        user
                );

        return messageRepository
                .findByConversationIdOrderByCreatedAtAsc(
                        conversation.getId()
                )
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    public MessageResponse addUserMessage(
            Long projectId,
            Long conversationId,
            CreateMessageRequest request
    ) {

        User user = getAuthenticatedUser();

        Conversation conversation =
                getOwnedConversation(
                        projectId,
                        conversationId,
                        user
                );

        Message message = Message.builder()
                .conversation(conversation)
                .role(MessageRole.USER)
                .content(request.getContent())
                .build();

        conversation.setUpdatedAt(LocalDateTime.now());

        messageRepository.save(message);

        return toMessageResponse(message);
    }

    public MessageResponse addAssistantMessage(
            Long projectId,
            Long conversationId,
            String content
    ) {

        User user = getAuthenticatedUser();

        Conversation conversation =
                getOwnedConversation(
                        projectId,
                        conversationId,
                        user
                );

        Message message = Message.builder()
                .conversation(conversation)
                .role(MessageRole.ASSISTANT)
                .content(content)
                .build();

        conversation.setUpdatedAt(LocalDateTime.now());

        messageRepository.save(message);

        return toMessageResponse(message);
    }

    private Conversation getOwnedConversation(
            Long projectId,
            Long conversationId,
            User user
    ) {

        Conversation conversation =
                conversationRepository
                        .findByIdAndProjectId(
                                conversationId,
                                projectId
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Conversation not found"
                                )
                        );

        if (!conversation.getProject()
                .getUser()
                .getId()
                .equals(user.getId())) {

            throw new RuntimeException(
                    "You do not have access to this conversation"
            );
        }

        return conversation;
    }

    private Project getOwnedProject(
            Long projectId,
            User user
    ) {

        Project project =
                projectRepository.findById(projectId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Project not found"
                                )
                        );

        if (!project.getUser()
                .getId()
                .equals(user.getId())) {

            throw new RuntimeException(
                    "You do not have access to this project"
            );
        }

        return project;
    }

    private User getAuthenticatedUser() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated()) {

            throw new RuntimeException(
                    "User is not authenticated"
            );
        }

        return userRepository
                .findByEmail(
                        authentication.getName().toLowerCase()
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Authenticated user not found"
                        )
                );
    }

    private ConversationResponse toConversationResponse(
            Conversation conversation
    ) {

        return ConversationResponse.builder()
                .id(conversation.getId())
                .projectId(
                        conversation.getProject().getId()
                )
                .title(conversation.getTitle())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    private MessageResponse toMessageResponse(
            Message message
    ) {

        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(
                        message.getConversation().getId()
                )
                .role(message.getRole())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }
}