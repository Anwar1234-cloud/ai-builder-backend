package com.aibuilder.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateProjectFileRequest {

    @NotBlank(message = "File path is required")
    @Size(max = 500, message = "File path cannot exceed 500 characters")
    private String path;

    @NotBlank(message = "File content is required")
    private String content;

    @Size(max = 50, message = "Language cannot exceed 50 characters")
    private String language;
}