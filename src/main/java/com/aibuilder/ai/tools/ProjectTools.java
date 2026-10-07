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
import com.aibuilder.workspace.service.WorkspaceFileCacheService;
import java.util.Optional;

import java.util.List;



@Component
@RequiredArgsConstructor
public class ProjectTools {

    private final ProjectFileRepository projectFileRepository;
    private final ProjectRepository projectRepository;
    private final AgentToolCallService agentToolCallService;
    private final ObjectMapper objectMapper;
    private final WorkspaceFileCacheService workspaceFileCacheService;


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

// First check whether this file was already
// read during this agent run.
            String cachedContent =
                    workspaceFileCacheService.get(
                            agentRunId,
                            path
                    );

            if (cachedContent != null) {

                agentToolCallService.completeToolCall(
                        toolCallId
                );

                return """
            FILE READ SUCCESSFULLY

            Path: %s
            Cached: true

            Content:
            --------------------
            %s
            --------------------
            """.formatted(
                        path,
                        cachedContent
                );
            }

            Optional<ProjectFile> fileOpt =
                    projectFileRepository.findByProjectIdAndPath(
                            projectId,
                            path
                    );


            if (fileOpt.isEmpty()) {

                agentToolCallService.completeToolCall(toolCallId);

                return """
                    FILE NOT FOUND

                    Path: %s

                    This file does not exist in the project.
                    If the task requires this file, create it using createFile.
                    Do not call readFile for this path again.
                    """.formatted(path);
            }

            ProjectFile file = fileOpt.get();

            String language =
                    file.getLanguage() == null
                            ? ""
                            : file.getLanguage();

            String fileContent =
                    file.getContent() == null
                            ? ""
                            : file.getContent();

            workspaceFileCacheService.put(
                    agentRunId,
                    path,
                    fileContent
            );

            agentToolCallService.completeToolCall(toolCallId);

            return """
                FILE READ SUCCESSFULLY

                Path: %s
                Language: %s

                Content:
                --------------------
                %s
                --------------------
                """.formatted(
                    path,
                    language,
                    fileContent
            );

        } catch (Exception e) {

            String message =
                    e.getMessage() == null
                            ? "Unknown error while reading file"
                            : e.getMessage();

            agentToolCallService.failToolCall(toolCallId, message);

            return """
                READ FILE ERROR

                Path: %s
                Error: %s

                Do not repeatedly call readFile for this path.
                """.formatted(path, message);
        }
    }
    @Tool(
            name = "readFiles",
            description = "Read the contents of multiple existing project files in one call. " +
                    "Use this when several files are needed for understanding or UI work. " +
                    "Do not use this just to check whether files exist; use the workspace manifest for that."
    )
    public String readFiles(
            @ToolParam(description = "List of project-relative file paths to read")
            List<String> paths,
            ToolContext context
    ) {

        if (paths == null || paths.isEmpty()) {
            return """
                {
                  "success": false,
                  "error": "NO_FILES",
                  "message": "No file paths were provided."
                }
                """;
        }

        Long projectId = getProjectId(context);
        Long agentRunId = getAgentRunId(context);
        Long agentTaskId = getAgentTaskId(context);

        String targetPath =
                String.join(", ", paths);

        Long toolCallId =
                agentToolCallService.startToolCall(
                        agentRunId,
                        agentTaskId,
                        "readFiles",
                        targetPath
                );

        try {

            StringBuilder result =
                    new StringBuilder();

            result.append(
                    "{\"success\":true,\"files\":["
            );

            for (int i = 0; i < paths.size(); i++) {

                String path = paths.get(i);

                validatePath(path);

                if (i > 0) {
                    result.append(",");
                }



                String cachedContent =
                        workspaceFileCacheService.get(
                                agentRunId,
                                path
                        );

                if (cachedContent != null) {

                    result.append("""
                        {
                          "path": %s,
                          "success": true,
                          "content": %s,
                          "cached": true
                        }
                        """.formatted(
                            objectMapper.writeValueAsString(path),
                            objectMapper.writeValueAsString(cachedContent)
                    ));

                    continue;
                }



                Optional<ProjectFile> fileOpt =
                        projectFileRepository
                                .findByProjectIdAndPath(
                                        projectId,
                                        path
                                );

                if (fileOpt.isEmpty()) {

                    result.append("""
                        {
                          "path": %s,
                          "success": false,
                          "error": "FILE_NOT_FOUND"
                        }
                        """.formatted(
                            objectMapper.writeValueAsString(path)
                    ));

                    continue;
                }

                ProjectFile file =
                        fileOpt.get();

                String fileContent =
                        file.getContent() == null
                                ? ""
                                : file.getContent();



                workspaceFileCacheService.put(
                        agentRunId,
                        path,
                        fileContent
                );



                result.append("""
                    {
                      "path": %s,
                      "success": true,
                      "language": %s,
                      "content": %s,
                      "cached": false
                    }
                    """.formatted(
                        objectMapper.writeValueAsString(path),

                        objectMapper.writeValueAsString(
                                file.getLanguage() == null
                                        ? ""
                                        : file.getLanguage()
                        ),

                        objectMapper.writeValueAsString(
                                fileContent
                        )
                ));
            }

            result.append("]}");

            String response =
                    result.toString();

            agentToolCallService.completeToolCall(
                    toolCallId
            );

            return response;

        } catch (Exception e) {

            String errorJson = """
                {
                  "success": false,
                  "error": "READ_FILES_ERROR",
                  "message": %s
                }
                """.formatted(
                    objectMapper.writeValueAsString(
                            e.getMessage() == null
                                    ? "Unknown error"
                                    : e.getMessage()
                    )
            );

            agentToolCallService.failToolCall(
                    toolCallId,
                    errorJson
            );

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

        Long projectId = getProjectId(toolContext);
        Long agentRunId = getAgentRunId(toolContext);
        Long agentTaskId = getAgentTaskId(toolContext);

        Long toolCallId =
                agentToolCallService.startToolCall(
                        agentRunId,
                        agentTaskId,
                        "writeFile",
                        path
                );

        try {

            validatePath(path);

            if (content == null) {
                throw new IllegalArgumentException(
                        "File content is required"
                );
            }

            Optional<ProjectFile> fileOpt =
                    projectFileRepository
                            .findByProjectIdAndPath(
                                    projectId,
                                    path
                            );

            if (fileOpt.isEmpty()) {

                String response = """
                    {
                      "success": false,
                      "error": "FILE_NOT_FOUND",
                      "path": %s,
                      "message": "The file does not exist. Use createFile if this task requires a new file."
                    }
                    """.formatted(
                        toJson(path)
                );

                agentToolCallService.failToolCall(
                        toolCallId,
                        response
                );

                return response;
            }

            ProjectFile file = fileOpt.get();

            file.setContent(content);
            file.setLanguage(detectLanguage(path));

            projectFileRepository.save(file);

            workspaceFileCacheService.put(
                    agentRunId,
                    path,
                    content
            );

            agentToolCallService.completeToolCall(
                    toolCallId
            );

            return """
                {
                  "success": true,
                  "action": "UPDATED",
                  "path": %s,
                  "message": "File updated successfully."
                }
                """.formatted(
                    toJson(path)
            );

        } catch (Exception e) {

            String message =
                    e.getMessage() == null
                            ? "Unknown error while writing file"
                            : e.getMessage();

            String response = """
                {
                  "success": false,
                  "error": "WRITE_FILE_ERROR",
                  "path": %s,
                  "message": %s
                }
                """.formatted(
                    toJson(path),
                    toJson(message)
            );

            agentToolCallService.failToolCall(
                    toolCallId,
                    response
            );

            return response;
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

        Long projectId = getProjectId(toolContext);
        Long agentRunId = getAgentRunId(toolContext);
        Long agentTaskId = getAgentTaskId(toolContext);

        Long toolCallId =
                agentToolCallService.startToolCall(
                        agentRunId,
                        agentTaskId,
                        "createFile",
                        path
                );

        try {

            validatePath(path);

            if (content == null) {
                throw new IllegalArgumentException(
                        "File content is required"
                );
            }

            Project project =
                    projectRepository.findById(projectId)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Project not found: " + projectId
                                    )
                            );

            boolean exists =
                    projectFileRepository
                            .existsByProjectIdAndPath(
                                    projectId,
                                    path
                            );

            if (exists) {

                String response = """
                    {
                      "success": false,
                      "error": "FILE_ALREADY_EXISTS",
                      "path": %s,
                      "message": "The file already exists. Use readFile and writeFile instead."
                    }
                    """.formatted(
                        toJson(path)
                );

                agentToolCallService.failToolCall(
                        toolCallId,
                        response
                );

                return response;
            }

            ProjectFile file = new ProjectFile();

            file.setPath(path);
            file.setContent(content);
            file.setLanguage(detectLanguage(path));
            file.setProject(project);

            projectFileRepository.save(file);

            workspaceFileCacheService.put(
                    agentRunId,
                    path,
                    content
            );

            agentToolCallService.completeToolCall(
                    toolCallId
            );

            return """
                {
                  "success": true,
                  "action": "CREATED",
                  "path": %s,
                  "message": "File created successfully."
                }
                """.formatted(
                    toJson(path)
            );

        } catch (Exception e) {

            String message =
                    e.getMessage() == null
                            ? "Unknown error while creating file"
                            : e.getMessage();

            String response = """
                {
                  "success": false,
                  "error": "CREATE_FILE_ERROR",
                  "path": %s,
                  "message": %s
                }
                """.formatted(
                    toJson(path),
                    toJson(message)
            );

            agentToolCallService.failToolCall(
                    toolCallId,
                    response
            );

            return response;
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

        Long projectId = getProjectId(toolContext);
        Long agentRunId = getAgentRunId(toolContext);
        Long agentTaskId = getAgentTaskId(toolContext);

        Long toolCallId =
                agentToolCallService.startToolCall(
                        agentRunId,
                        agentTaskId,
                        "deleteFile",
                        path
                );

        try {

            validatePath(path);

            Optional<ProjectFile> fileOpt =
                    projectFileRepository
                            .findByProjectIdAndPath(
                                    projectId,
                                    path
                            );

            if (fileOpt.isEmpty()) {

                String response = """
                    {
                      "success": false,
                      "error": "FILE_NOT_FOUND",
                      "path": %s,
                      "message": "The file does not exist. Nothing was deleted."
                    }
                    """.formatted(
                        toJson(path)
                );

                agentToolCallService.failToolCall(
                        toolCallId,
                        response
                );

                return response;
            }

            projectFileRepository.delete(
                    fileOpt.get()
            );

            workspaceFileCacheService.remove(
                    agentRunId,
                    path
            );

            agentToolCallService.completeToolCall(
                    toolCallId
            );

            return """
                {
                  "success": true,
                  "action": "DELETED",
                  "path": %s,
                  "message": "File deleted successfully."
                }
                """.formatted(
                    toJson(path)
            );

        } catch (Exception e) {

            String message =
                    e.getMessage() == null
                            ? "Unknown error while deleting file"
                            : e.getMessage();

            String response = """
                {
                  "success": false,
                  "error": "DELETE_FILE_ERROR",
                  "path": %s,
                  "message": %s
                }
                """.formatted(
                    toJson(path),
                    toJson(message)
            );

            agentToolCallService.failToolCall(
                    toolCallId,
                    response
            );

            return response;
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

    private String toJson(String value) {
        try {
            return objectMapper.writeValueAsString(
                    value == null ? "" : value
            );
        } catch (Exception e) {
            return "\"\"";
        }
    }
}