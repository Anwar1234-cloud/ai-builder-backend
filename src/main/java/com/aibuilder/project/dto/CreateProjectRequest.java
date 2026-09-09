package com.aibuilder.project.dto;

import com.aibuilder.project.entity.ProjectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateProjectRequest {

    @NotBlank(message = "Project name is required")
    @Size(max = 150, message = "Project name cannot exceed 150 characters")
    private String name;

    @Size(max = 5000, message = "Description cannot exceed 5000 characters")
    private String description;

    @NotNull(message = "Project type is required")
    private ProjectType type;
}