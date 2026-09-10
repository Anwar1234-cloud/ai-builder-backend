package com.aibuilder.project.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ProjectVersionResponse {

    private Long id;
    private Long projectId;
    private Integer versionNumber;
    private String message;
    private String source;
    private LocalDateTime createdAt;
}