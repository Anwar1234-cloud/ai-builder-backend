package com.aibuilder.build.entity;

import com.aibuilder.agent.entity.AgentRun;
import com.aibuilder.project.entity.Project;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "build_runs",
        indexes = {
                @Index(
                        name = "idx_build_run_project_id",
                        columnList = "project_id"
                ),
                @Index(
                        name = "idx_build_run_agent_run_id",
                        columnList = "agent_run_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class BuildRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_run_id")
    private AgentRun agentRun;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BuildStatus status;

    @Column(columnDefinition = "TEXT")
    private String command;

    @Column(columnDefinition = "TEXT")
    private String output;

    @Column(columnDefinition = "TEXT")
    private String errorOutput;

    @Column(length = 1000)
    private String workspacePath;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = BuildStatus.QUEUED;
        }

        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }
}