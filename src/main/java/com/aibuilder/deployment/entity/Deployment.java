package com.aibuilder.deployment.entity;

import com.aibuilder.build.entity.BuildRun;
import com.aibuilder.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "deployments",
        indexes = {
                @Index(
                        name = "idx_deployment_project",
                        columnList = "project_id"
                ),
                @Index(
                        name = "idx_deployment_build",
                        columnList = "build_id"
                ),
                @Index(
                        name = "idx_deployment_status",
                        columnList = "status"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Deployment {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;


    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "project_id",
            nullable = false
    )
    private Project project;


    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "build_id",
            nullable = false
    )
    private BuildRun buildRun;


    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private DeploymentProvider provider;


    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private DeploymentStatus status;


    @Column(
            name = "provider_deployment_id",
            length = 255
    )
    private String providerDeploymentId;


    @Column(
            name = "deployment_url",
            length = 1000
    )
    private String deploymentUrl;


    @Column(
            name = "error_message",
            columnDefinition = "TEXT"
    )
    private String errorMessage;


    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;


    @Column(
            name = "started_at"
    )
    private LocalDateTime startedAt;


    @Column(
            name = "completed_at"
    )
    private LocalDateTime completedAt;


    @PrePersist
    protected void onCreate() {

        if (status == null) {
            status =
                    DeploymentStatus.PENDING;
        }

        if (provider == null) {
            provider =
                    DeploymentProvider.VERCEL;
        }

        if (createdAt == null) {
            createdAt =
                    LocalDateTime.now();
        }
    }
}