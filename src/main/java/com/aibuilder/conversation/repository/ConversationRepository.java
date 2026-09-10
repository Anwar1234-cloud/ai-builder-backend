package com.aibuilder.conversation.repository;

import com.aibuilder.conversation.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository
        extends JpaRepository<Conversation, Long> {

    List<Conversation> findByProjectIdOrderByUpdatedAtDesc(Long projectId);

    Optional<Conversation> findByIdAndProjectId(
            Long id,
            Long projectId
    );
}