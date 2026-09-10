package com.aibuilder.ai.tools;

import com.aibuilder.project.repository.ProjectRepository;
import com.aibuilder.workspace.entity.ProjectFile;
import com.aibuilder.workspace.repository.ProjectFileRepository;
import com.aibuilder.project.entity.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProjectTools {

    private final ProjectFileRepository projectFileRepository;
    private final ProjectRepository projectRepository;

    @Tool(
            name = "listFiles",
            description = """
                    List all files in the current project.

                    Use this tool when you need to inspect the existing
                    project structure before creating or modifying files.

                    The current project is provided automatically by the system.
                    Do not ask the user for a project ID.
                    """
    )
    public List<ProjectFileInfo> listFiles(ToolContext toolContext) {

        Object projectIdValue =
                toolContext.getContext().get("projectId");

        if (projectIdValue == null) {
            throw new IllegalStateException(
                    "Project ID is missing from tool context"
            );
        }

        Long projectId = ((Number) projectIdValue).longValue();

        List<ProjectFile> files =
                projectFileRepository
                        .findByProjectIdOrderByPathAsc(projectId);

        return files.stream()
                .map(file -> new ProjectFileInfo(
                        file.getId(),
                        file.getPath(),
                        file.getLanguage()
                ))
                .toList();
    }

    @Tool(
            name = "readFile",
            description = """
                    Read the complete contents of a file in the current project.

                    Use this tool when you need to understand an existing file
                    before modifying it.

                    The file path must be an existing project file.
                    """
    )
    public String readFile(
            String path,
            ToolContext toolContext
    ) {

        Object projectIdValue =
                toolContext.getContext().get("projectId");

        if (projectIdValue == null) {
            throw new IllegalStateException(
                    "Project ID is missing from tool context"
            );
        }

        Long projectId = ((Number) projectIdValue).longValue();

        ProjectFile file =
                projectFileRepository
                        .findByProjectIdAndPath(projectId, path)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "File not found: " + path
                                )
                        );

        return file.getContent();
    }

    @Tool(
            name = "writeFile",
            description = """
        Update the contents of an existing file in the current project.

        Use this tool ONLY when the file already exists.

        Never use this tool to simulate deleting a file.
        Never replace a file with an empty or placeholder file when
        the user asks to delete it.

        For creating a new file, use createFile.
        For deleting a file, use deleteFile.
        """
    )
    public String writeFile(
            String path,
            String content,
            ToolContext toolContext
    ) {

        Object projectIdValue =
                toolContext.getContext().get("projectId");

        if (projectIdValue == null) {
            throw new IllegalStateException(
                    "Project ID is missing from tool context"
            );
        }

        Long projectId =
                ((Number) projectIdValue).longValue();

        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(
                    "File path is required"
            );
        }

        if (content == null) {
            throw new IllegalArgumentException(
                    "File content is required"
            );
        }

        // Prevent path traversal.
        if (path.startsWith("/")
                || path.startsWith("\\")
                || path.contains("..")) {

            throw new IllegalArgumentException(
                    "Invalid project file path: " + path
            );
        }

        Project project =
                projectRepository.findById(projectId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Project not found: " + projectId
                                )
                        );

        ProjectFile file =
                projectFileRepository
                        .findByProjectIdAndPath(projectId, path)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "File not found: " + path
                                )
                        );

        file.setContent(content);

        projectFileRepository.save(file);

        return "File updated successfully: " + path;
    }

    @Tool(
            name = "createFile",
            description = """
                Create a new file in the current project.

                Use this tool when a new file is required.

                Do not use this tool to modify an existing file.
                Use writeFile for existing files.

                The path must be a relative project path such as:
                src/components/Navbar.jsx
                src/data/menu.js
                app/page.tsx
                """
    )
    public String createFile(
            String path,
            String content,
            ToolContext toolContext
    ) {

        Object projectIdValue =
                toolContext.getContext().get("projectId");

        if (projectIdValue == null) {
            throw new IllegalStateException(
                    "Project ID is missing from tool context"
            );
        }

        Long projectId =
                ((Number) projectIdValue).longValue();

        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(
                    "File path is required"
            );
        }

        if (content == null) {
            throw new IllegalArgumentException(
                    "File content is required"
            );
        }

        // Prevent path traversal.
        if (path.startsWith("/")
                || path.startsWith("\\")
                || path.contains("..")) {

            throw new IllegalArgumentException(
                    "Invalid project file path: " + path
            );
        }

        // Make sure the project exists.
        Project project =
                projectRepository.findById(projectId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Project not found: " + projectId
                                )
                        );

        // Do not overwrite an existing file.
        boolean exists =
                projectFileRepository
                        .existsByProjectIdAndPath(
                                projectId,
                                path
                        );

        if (exists) {
            throw new IllegalArgumentException(
                    "File already exists: " + path
            );
        }

        ProjectFile file = new ProjectFile();

        file.setPath(path);
        file.setContent(content);
        file.setLanguage(detectLanguage(path));
        file.setProject(project);

        projectFileRepository.save(file);

        return "File created successfully: " + path;
    }

    private String detectLanguage(String path) {

        int dotIndex = path.lastIndexOf('.');

        if (dotIndex == -1 || dotIndex == path.length() - 1) {
            return "text";
        }

        String extension =
                path.substring(dotIndex + 1)
                        .toLowerCase();

        return switch (extension) {
            case "js" -> "javascript";
            case "jsx" -> "jsx";
            case "ts" -> "typescript";
            case "tsx" -> "tsx";
            case "java" -> "java";
            case "html" -> "html";
            case "css" -> "css";
            case "json" -> "json";
            case "md" -> "markdown";
            case "py" -> "python";
            default -> extension;
        };
    }

    @Tool(
            name = "deleteFile",
            description = """
                Delete an existing file from the current project.

                Use this tool only when the user explicitly asks to
                remove a file.

                Never delete a file merely because it appears unnecessary.
                """
    )
    public String deleteFile(
            String path,
            ToolContext toolContext
    ) {

        Object projectIdValue =
                toolContext.getContext().get("projectId");

        if (projectIdValue == null) {
            throw new IllegalStateException(
                    "Project ID is missing from tool context"
            );
        }

        Long projectId =
                ((Number) projectIdValue).longValue();

        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(
                    "File path is required"
            );
        }

        if (path.startsWith("/")
                || path.startsWith("\\")
                || path.contains("..")) {

            throw new IllegalArgumentException(
                    "Invalid project file path: " + path
            );
        }

        ProjectFile file =
                projectFileRepository
                        .findByProjectIdAndPath(projectId, path)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "File not found: " + path
                                )
                        );

        projectFileRepository.delete(file);

        return "File deleted successfully: " + path;
    }

    public record ProjectFileInfo(
            Long id,
            String path,
            String language
    ) {
    }
}