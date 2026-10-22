
package com.aibuilder.fullstack.entity;

import com.aibuilder.agent.entity.AgentRun;
import com.aibuilder.project.entity.Project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "full_stack_architectures",
        indexes = {
                @Index(
                        name = "idx_fullstack_arch_project",
                        columnList = "project_id"
                )
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_fullstack_arch_agent_run",
                        columnNames = "agent_run_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class FullStackArchitecture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_run_id", nullable = false)
    private AgentRun agentRun;

    @Column(name = "plan_json", nullable = false, columnDefinition = "TEXT")
    private String planJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
