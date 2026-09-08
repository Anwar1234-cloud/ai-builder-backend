package com.aibuilder.project.dto;

import com.aibuilder.project.entity.ProjectType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ProjectResponse {

    private Long id;
    private String name;
    private String description;
    private ProjectType type;
    private Long userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}