package com.aibuilder.workspace.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ProjectFileResponse {

    private Long id;
    private Long projectId;
    private String path;
    private String content;
    private String language;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}