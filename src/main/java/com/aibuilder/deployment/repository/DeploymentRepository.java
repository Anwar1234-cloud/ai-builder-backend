package com.aibuilder.deployment.repository;

import com.aibuilder.deployment.entity.Deployment;
import com.aibuilder.deployment.entity.DeploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeploymentRepository
        extends JpaRepository<Deployment, Long> {

    List<Deployment>
    findByProjectIdOrderByCreatedAtDesc(
            Long projectId
    );

    Optional<Deployment>
    findFirstByProjectIdOrderByCreatedAtDesc(
            Long projectId
    );

    Optional<Deployment>
    findFirstByProjectIdAndStatusOrderByCreatedAtDesc(
            Long projectId,
            DeploymentStatus status
    );

    boolean existsByBuildRunIdAndStatus(
            Long buildRunId,
            DeploymentStatus status
    );

    boolean existsByBuildRunIdAndStatusIn(
            Long buildRunId,
            List<DeploymentStatus> statuses
    );
}