package com.aibuilder.agent.controller;

import com.aibuilder.agent.dto.AgentRunResponse;
import com.aibuilder.agent.service.AgentRunService;
import com.aibuilder.agent.service.AgentToolCallService;
import com.aibuilder.agent.dto.AgentToolCallResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/agent-runs")
@RequiredArgsConstructor
public class AgentRunController {

    private final AgentRunService agentRunService;
    private final AgentToolCallService agentToolCallService;

    @GetMapping("/{runId}")
    public ResponseEntity<AgentRunResponse> getRun(
            @PathVariable Long projectId,
            @PathVariable Long runId
    ) {

        return ResponseEntity.ok(
                AgentRunResponse.from(
                        agentRunService.getRun(
                                projectId,
                                runId
                        )
                )
        );
    }

    @GetMapping("/{runId}/tool-calls")
    public ResponseEntity<List<AgentToolCallResponse>> getToolCalls(
            @PathVariable Long projectId,
            @PathVariable Long runId
    ) {

        // This also verifies that the run belongs to the project.
        agentRunService.getRun(projectId, runId);

        return ResponseEntity.ok(
                agentToolCallService
                        .getToolCalls(runId)
                        .stream()
                        .map(AgentToolCallResponse::from)
                        .toList()
        );
    }
    @GetMapping("/{runId}/tasks/{taskId}/tool-calls")
    public ResponseEntity<List<AgentToolCallResponse>> getTaskToolCalls(
            @PathVariable Long projectId,
            @PathVariable Long runId,
            @PathVariable Long taskId
    ) {

        agentRunService.getRun(
                projectId,
                runId
        );

        return ResponseEntity.ok(
                agentToolCallService
                        .getToolCallsForTask(taskId)
                        .stream()
                        .map(AgentToolCallResponse::from)
                        .toList()
        );
    }
}