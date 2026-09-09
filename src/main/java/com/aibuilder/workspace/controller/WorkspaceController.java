package com.aibuilder.workspace.controller;

import com.aibuilder.workspace.dto.CreateProjectFileRequest;
import com.aibuilder.workspace.dto.ProjectFileResponse;
import com.aibuilder.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/files")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    public ResponseEntity<ProjectFileResponse> createFile(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateProjectFileRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        workspaceService.createFile(
                                projectId,
                                request
                        )
                );
    }

    @GetMapping
    public ResponseEntity<List<ProjectFileResponse>> getFiles(
            @PathVariable Long projectId
    ) {

        return ResponseEntity.ok(
                workspaceService.getFiles(projectId)
        );
    }

    @GetMapping("/content")
    public ResponseEntity<ProjectFileResponse> getFile(
            @PathVariable Long projectId,
            @RequestParam String path
    ) {

        return ResponseEntity.ok(
                workspaceService.getFile(
                        projectId,
                        path
                )
        );
    }

    @PutMapping("/content")
    public ResponseEntity<ProjectFileResponse> updateFile(
            @PathVariable Long projectId,
            @RequestParam String path,
            @Valid @RequestBody CreateProjectFileRequest request
    ) {

        return ResponseEntity.ok(
                workspaceService.updateFile(
                        projectId,
                        path,
                        request
                )
        );
    }

    @DeleteMapping("/content")
    public ResponseEntity<Void> deleteFile(
            @PathVariable Long projectId,
            @RequestParam String path
    ) {

        workspaceService.deleteFile(
                projectId,
                path
        );

        return ResponseEntity.noContent().build();
    }
}