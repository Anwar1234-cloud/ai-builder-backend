package com.aibuilder.ai.tools;

import com.aibuilder.agent.entity.AgentToolCall;
import com.aibuilder.project.repository.ProjectRepository;
import com.aibuilder.workspace.entity.ProjectFile;
import com.aibuilder.workspace.repository.ProjectFileRepository;
import com.aibuilder.project.entity.Project;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import com.aibuilder.agent.service.AgentToolCallService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.util.Optional;

import java.util.List;

import static io.micrometer.core.instrument.util.StringEscapeUtils.escapeJson;

@Component
@RequiredArgsConstructor
public class ProjectTools {

    private final ProjectFileRepository projectFileRepository;
    private final ProjectRepository projectRepository;
    private final AgentToolCallService agentToolCallService;
    private final ObjectMapper objectMapper;

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

        Long agentTaskId =
                getAgentTaskId(toolContext);

        Long toolCallId =
                agentToolCallService.startToolCall(
                        agentRunId,
                        agentTaskId,
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
    private Long getAgentTaskId(ToolContext toolContext) {

        Object value =
                toolContext.getContext()
                        .get("agentTaskId");

        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        return Long.valueOf(value.toString());
    }

    @Tool(
            name = "readFile",
            description = "Read the contents of an existing project file. " +
                    "Use this before modifying a file. " +
                    "If the file does not exist, return a structured error instead of throwing."
    )
    public String readFile(
            @ToolParam(description = "Project-relative file path") String path,
            ToolContext context
    ) {

        Long projectId = getProjectId(context);
        Long agentRunId = getAgentRunId(context);
        Long agentTaskId = getAgentTaskId(context);

        Long toolCallId = agentToolCallService.startToolCall(
                agentRunId,
                agentTaskId,
                "readFile",
                path
        );

        try {
            validatePath(path);

            Optional<ProjectFile> fileOpt =
                    projectFileRepository.findByProjectIdAndPath(projectId, path);

            if (fileOpt.isEmpty()) {
                String errorJson = """
                {
                  "success": false,
                  "error": "FILE_NOT_FOUND",
                  "path": "%s",
                  "message": "File does not exist in the project. Create it if required."
                }
                """.formatted(path);

                agentToolCallService.completeToolCall(toolCallId);
                return errorJson;
            }

            ProjectFile file = fileOpt.get();

            String result = """
            {
              "success": true,
              "path": "%s",
              "content": %s,
              "language": "%s"
            }
            """.formatted(
                    path,
                    objectMapper.writeValueAsString(file.getContent()),
                    file.getLanguage() == null ? "" : file.getLanguage()
            );

            agentToolCallService.completeToolCall(toolCallId);
            return result;

        } catch (Exception e) {

            String errorJson = """
            {
              "success": false,
              "error": "READ_FILE_ERROR",
              "path": "%s",
              "message": "%s"
            }
            """.formatted(
                    path,
                    escapeJson(e.getMessage())
            );

            agentToolCallService.failToolCall(toolCallId, e.getMessage());
            return errorJson;
        }
    }

    private Long getProjectId(ToolContext context) {
        if (context == null || context.getContext() == null) {
            throw new IllegalStateException("ToolContext is missing");
        }

        Object value = context.getContext().get("projectId");

        if (value == null) {
            throw new IllegalStateException("projectId is missing from ToolContext");
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        return Long.parseLong(value.toString());
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
        Long agentTaskId =
                getAgentTaskId(toolContext);

        Long toolCallId =
                agentToolCallService.startToolCall(
                        agentRunId,
                        agentTaskId,
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

                Call listFiles ONCE at the start of your work to see all
                existing files. Use that list to decide whether each file
                you need already exists.

                If a file is NOT in that list, call createFile directly —
                do not call readFile just to check if it exists.

                If a file IS in that list and needs changes, use readFile
                then writeFile instead.

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
        Long agentTaskId =
                getAgentTaskId(toolContext);

        Long toolCallId =
                agentToolCallService.startToolCall(
                        agentRunId,
                        agentTaskId,
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

            return "TOOL_ERROR: " + e.getMessage()
                    + ". The operation was not completed. "
                    + "Inspect the existing project and choose the appropriate action.";
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
        Long agentTaskId =
                getAgentTaskId(toolContext);

        Long toolCallId =
                agentToolCallService.startToolCall(
                        agentRunId,
                        agentTaskId,
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
    private void validatePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("File path cannot be empty");
        }

        String normalized = path.replace('\\', '/');

        if (normalized.startsWith("/")) {
            throw new IllegalArgumentException("Absolute paths are not allowed");
        }

        if (normalized.matches("^[A-Za-z]:/.*")) {
            throw new IllegalArgumentException("Absolute paths are not allowed");
        }

        String[] parts = normalized.split("/");

        for (String part : parts) {
            if ("..".equals(part)) {
                throw new IllegalArgumentException("Path traversal is not allowed");
            }
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}