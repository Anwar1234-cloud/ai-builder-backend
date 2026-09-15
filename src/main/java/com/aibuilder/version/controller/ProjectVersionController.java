
package com.aibuilder.version.controller;

import com.aibuilder.version.entity.ProjectVersion;
import com.aibuilder.version.entity.ProjectVersionFile;
import com.aibuilder.version.service.ProjectVersionService;
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
    public ResponseEntity<ProjectVersion> createVersion(
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
    public ResponseEntity<List<ProjectVersion>> getVersions(
            @PathVariable Long projectId
    ) {

        return ResponseEntity.ok(
                projectVersionService.getVersions(projectId)
        );
    }



    @PostMapping("/snapshot")
    public ResponseEntity<ProjectVersion> createSnapshot(
            @PathVariable Long projectId,
            @RequestParam String message,
            @RequestParam(defaultValue = "SYSTEM") String source
    ) {

        return ResponseEntity.ok(
                projectVersionService.createSnapshot(
                        projectId,
                        message,
                        source
                )
        );
    }



    @GetMapping("/{versionNumber}")
    public ResponseEntity<ProjectVersion> getVersion(
            @PathVariable Long projectId,
            @PathVariable Integer versionNumber
    ) {

        return ResponseEntity.ok(
                projectVersionService.getVersion(
                        projectId,
                        versionNumber
                )
        );
    }



    @GetMapping("/{versionNumber}/files")
    public ResponseEntity<List<ProjectVersionFile>> getSnapshotFiles(
            @PathVariable Long projectId,
            @PathVariable Integer versionNumber
    ) {

        return ResponseEntity.ok(
                projectVersionService.getSnapshotFiles(
                        projectId,
                        versionNumber
                )
        );
    }
    @PostMapping("/{versionNumber}/restore")
    public ResponseEntity<ProjectVersion> restoreVersion(
            @PathVariable Long projectId,
            @PathVariable Integer versionNumber
    ) {
        return ResponseEntity.ok(
                projectVersionService.restoreVersion(
                        projectId,
                        versionNumber
                )
        );
    }
}

