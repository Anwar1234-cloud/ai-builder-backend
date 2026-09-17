package com.aibuilder.agent.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "agent_tool_calls",
        indexes = {
                @Index(
                        name = "idx_agent_tool_call_run_id",
                        columnList = "agent_run_id"
                ),
                @Index(
                        name = "idx_agent_tool_call_task_id",
                        columnList = "agent_task_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class AgentToolCall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_run_id", nullable = false)
    private AgentRun agentRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_task_id")
    private AgentTask agentTask;

    @Column(nullable = false, length = 100)
    private String toolName;

    @Column(length = 500)
    private String targetPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentToolCallStatus status;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }

        if (status == null) {
            status = AgentToolCallStatus.RUNNING;
        }
    }
}