package com.aibuilder.workspace.service;

import com.aibuilder.workspace.entity.ProjectFile;
import com.aibuilder.workspace.repository.ProjectFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkspaceManifestService {

    private final ProjectFileRepository projectFileRepository;

    public String buildManifest(Long projectId) {

        List<ProjectFile> files =
                projectFileRepository.findByProjectIdOrderByPathAsc(projectId);

        if (files.isEmpty()) {
            return """
                    WORKSPACE MANIFEST

                    Project contains no files.
                    """;
        }

        StringBuilder manifest =
                new StringBuilder();

        manifest.append("WORKSPACE MANIFEST\n\n");
        manifest.append("Project files:\n");

        for (ProjectFile file : files) {

            String language =
                    file.getLanguage() == null
                            ? "unknown"
                            : file.getLanguage();

            int size =
                    file.getContent() == null
                            ? 0
                            : file.getContent().length();

            manifest.append("- ")
                    .append(file.getPath())
                    .append(" | language=")
                    .append(language)
                    .append(" | size=")
                    .append(size)
                    .append("\n");
        }

        manifest.append("\nIMPORTANT:\n");
        manifest.append("- This manifest is the authoritative project structure.\n");
        manifest.append("- Do not use readFile just to discover whether a file exists.\n");
        manifest.append("- Use readFile only when the actual file content is required.\n");

        return manifest.toString();
    }
}