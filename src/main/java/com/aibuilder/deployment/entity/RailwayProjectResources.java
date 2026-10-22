
package com.aibuilder.deployment.entity;

import com.aibuilder.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "railway_project_resources",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_railway_resources_project",
                        columnNames = "project_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RailwayProjectResources {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "project_id",
            nullable = false,
            unique = true
    )
    private Project project;

    @Column(
            name = "railway_project_id",
            nullable = false,
            length = 255
    )
    private String railwayProjectId;

    @Column(
            name = "railway_environment_id",
            nullable = false,
            length = 255
    )
    private String railwayEnvironmentId;

    @Column(
            name = "railway_backend_service_id",
            nullable = false,
            length = 255
    )
    private String railwayBackendServiceId;

    @Column(
            name = "railway_postgres_service_id",
            length = 255
    )
    private String railwayPostgresServiceId;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
