package com.aibuilder.ai.tools;

import com.aibuilder.project.repository.ProjectRepository;
import com.aibuilder.workspace.entity.ProjectFile;
import com.aibuilder.workspace.repository.ProjectFileRepository;
import com.aibuilder.project.entity.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import com.aibuilder.agent.service.AgentToolCallService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProjectTools {

    private final ProjectFileRepository projectFileRepository;
    private final ProjectRepository projectRepository;
    private final AgentToolCallService agentToolCallService;

    @Tool(
            name = "listFiles",
            description = """
                List all files in the current project.

                Use this when you need to inspect the project's
                existing file structure.

                Do not ask the user for a project ID.
                """
    )
    public List<ProjectFileInfo> listFiles(
            ToolContext toolContext
    ) {

        Long projectId =
                ((Number) toolContext
                        .getContext()
                        .get("projectId"))
                        .longValue();

        Long agentRunId =
                getAgentRunId(toolContext);

        Long toolCallId =
                agentToolCallService.startToolCall(
                        agentRunId,
                        "listFiles",
                        null
                );

        try {

            List<ProjectFile> files =
                    projectFileRepository
                            .findByProjectIdOrderByPathAsc(projectId);

            List<ProjectFileInfo> result =
                    files.stream()
                            .map(file ->
                                    new ProjectFileInfo(
                                            file.getId(),
                                            file.getPath(),
                                            file.getLanguage()
                                    )
                            )
                            .toList();

            agentToolCallService.completeToolCall(toolCallId);

            return result;

        } catch (Exception e) {

            agentToolCallService.failToolCall(
                    toolCallId,
                    e.getMessage()
            );

            throw e;
        }
    }

    private Long getAgentRunId(ToolContext toolContext) {

        Object value =
                toolContext.getContext().get("agentRunId");

        if (value == null) {
            throw new IllegalStateException(
                    "Agent run ID is missing from tool context"
            );
        }

        return ((Number) value).longValue();
    }

    @Tool(
            name = "readFile",
            description = """
                Read the complete contents of an existing file
                in the current project.

                Use this before modifying an existing file.
                """
    )
    public String readFile(
            String path,
            ToolContext toolContext
    ) {

        Long projectId =
                ((Number) toolContext
                        .getContext()
                        .get("projectId"))
                        .longValue();

        Long agentRunId =
                getAgentRunId(toolContext);

        Long toolCallId =
                agentToolCallService.startToolCall(
                        agentRunId,
                        "readFile",
                        path
                );

        try {

            ProjectFile file =
                    projectFileRepository
                            .findByProjectIdAndPath(
                                    projectId,
                                    path
                            )
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "File not found: " + path
                                    )
                            );

            String content = file.getContent();

            agentToolCallService.completeToolCall(toolCallId);

            return content;

        } catch (Exception e) {

            agentToolCallService.failToolCall(
                    toolCallId,
                    e.getMessage()
            );

            throw e;
        }
    }

    @Tool(
            name = "writeFile",
            description = """
                Update the contents of an existing file.

                Use this ONLY when the file already exists.

                Always write the complete contents of the file.

                Never use this tool to simulate deleting a file.
                For a new file use createFile.
                """
    )
    public String writeFile(
            String path,
            String content,
            ToolContext toolContext
    ) {

        Long projectId =
                ((Number) toolContext
                        .getContext()
                        .get("projectId"))
                        .longValue();

        Long agentRunId =
                getAgentRunId(toolContext);

        Long toolCallId =
                agentToolCallService.startToolCall(
                        agentRunId,
                        "writeFile",
                        path
                );

        try {

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

            if (path.startsWith("/")
                    || path.startsWith("\\")
                    || path.contains("..")) {

                throw new IllegalArgumentException(
                        "Invalid project file path: " + path
                );
            }

            ProjectFile file =
                    projectFileRepository
                            .findByProjectIdAndPath(
                                    projectId,
                                    path
                            )
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "File not found: " + path
                                    )
                            );

            file.setContent(content);
            file.setLanguage(detectLanguage(path));

            projectFileRepository.save(file);

            agentToolCallService.completeToolCall(toolCallId);

            return "File updated successfully: " + path;

        } catch (Exception e) {

            agentToolCallService.failToolCall(
                    toolCallId,
                    e.getMessage()
            );

            throw e;
        }
    }

    @Tool(
            name = "createFile",
            description = """
                Create a new file in the current project.

                Use this only when the file does not already exist.

                Never overwrite an existing file with this tool.
                """
    )
    public String createFile(
            String path,
            String content,
            ToolContext toolContext
    ) {

        Long projectId =
                ((Number) toolContext
                        .getContext()
                        .get("projectId"))
                        .longValue();

        Long agentRunId =
                getAgentRunId(toolContext);

        Long toolCallId =
                agentToolCallService.startToolCall(
                        agentRunId,
                        "createFile",
                        path
                );

        try {

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
                                            "Project not found: "
                                                    + projectId
                                    )
                            );

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

            ProjectFile file =
                    new ProjectFile();

            file.setPath(path);
            file.setContent(content);
            file.setLanguage(detectLanguage(path));
            file.setProject(project);

            projectFileRepository.save(file);

            agentToolCallService.completeToolCall(toolCallId);

            return "File created successfully: " + path;

        } catch (Exception e) {

            agentToolCallService.failToolCall(
                    toolCallId,
                    e.getMessage()
            );

            throw e;
        }
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
                Permanently delete an existing file from the current project.

                Use this when the user explicitly asks to delete or remove
                a file.

                Never simulate deletion by writing placeholder content.
                """
    )
    public String deleteFile(
            String path,
            ToolContext toolContext
    ) {

        Long projectId =
                ((Number) toolContext
                        .getContext()
                        .get("projectId"))
                        .longValue();

        Long agentRunId =
                getAgentRunId(toolContext);

        Long toolCallId =
                agentToolCallService.startToolCall(
                        agentRunId,
                        "deleteFile",
                        path
                );

        try {

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
                            .findByProjectIdAndPath(
                                    projectId,
                                    path
                            )
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "File not found: " + path
                                    )
                            );

            projectFileRepository.delete(file);

            agentToolCallService.completeToolCall(toolCallId);

            return "File deleted successfully: " + path;

        } catch (Exception e) {

            agentToolCallService.failToolCall(
                    toolCallId,
                    e.getMessage()
            );

            throw e;
        }
    }

    public record ProjectFileInfo(
            Long id,
            String path,
            String language
    ) {
    }
}