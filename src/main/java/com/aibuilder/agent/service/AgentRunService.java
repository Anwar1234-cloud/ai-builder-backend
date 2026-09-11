package com.aibuilder.agent.service;

import com.aibuilder.agent.entity.AgentRun;
import com.aibuilder.agent.entity.AgentRunStatus;
import com.aibuilder.agent.repository.AgentRunRepository;
import com.aibuilder.conversation.entity.Conversation;
import com.aibuilder.conversation.repository.ConversationRepository;
import com.aibuilder.project.entity.Project;
import com.aibuilder.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AgentRunService {

    private final AgentRunRepository agentRunRepository;
    private final ProjectRepository projectRepository;
    private final ConversationRepository conversationRepository;

    @Transactional
    public AgentRun startRun(
            Long projectId,
            Long conversationId
    ) {

        Project project =
                projectRepository.findById(projectId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Project not found: " + projectId
                                )
                        );

        Conversation conversation =
                conversationRepository
                        .findByIdAndProjectId(
                                conversationId,
                                projectId
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Conversation not found: "
                                                + conversationId
                                )
                        );

        AgentRun run = new AgentRun();

        run.setProject(project);
        run.setConversation(conversation);
        run.setStatus(AgentRunStatus.RUNNING);
        run.setStartedAt(LocalDateTime.now());

        return agentRunRepository.save(run);
    }

    @Transactional
    public AgentRun completeRun(Long runId) {

        AgentRun run =
                agentRunRepository.findById(runId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Agent run not found: " + runId
                                )
                        );

        run.setStatus(AgentRunStatus.COMPLETED);
        run.setCompletedAt(LocalDateTime.now());

        return agentRunRepository.save(run);
    }

    @Transactional
    public AgentRun failRun(
            Long runId,
            String errorMessage
    ) {

        AgentRun run =
                agentRunRepository.findById(runId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Agent run not found: " + runId
                                )
                        );

        run.setStatus(AgentRunStatus.FAILED);
        run.setCompletedAt(LocalDateTime.now());
        run.setErrorMessage(errorMessage);

        return agentRunRepository.save(run);
    }
    @Transactional(readOnly = true)
    public AgentRun getRun(
            Long projectId,
            Long runId
    ) {

        return agentRunRepository
                .findByIdAndProjectId(runId, projectId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Agent run not found: " + runId
                        )
                );
    }
}