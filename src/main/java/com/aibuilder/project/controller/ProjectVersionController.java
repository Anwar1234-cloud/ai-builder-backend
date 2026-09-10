package com.aibuilder.project.controller;

import com.aibuilder.project.dto.ProjectVersionResponse;
import com.aibuilder.project.service.ProjectVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/versions")
@RequiredArgsConstructor
public class ProjectVersionController {

    private final ProjectVersionService projectVersionService;

    @PostMapping
    public ResponseEntity<ProjectVersionResponse> createVersion(
            @PathVariable Long projectId,
            @RequestParam String message,
            @RequestParam(defaultValue = "USER") String source
    ) {

        return ResponseEntity.ok(
                projectVersionService.createVersion(
                        projectId,
                        message,
                        source
                )
        );
    }

    @GetMapping
    public ResponseEntity<List<ProjectVersionResponse>> getVersions(
            @PathVariable Long projectId
    ) {

        return ResponseEntity.ok(
                projectVersionService.getVersions(projectId)
        );
    }
}