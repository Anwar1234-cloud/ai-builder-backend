package com.aibuilder.agent.controller;

import com.aibuilder.agent.entity.AgentTask;
import com.aibuilder.agent.service.AgentTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/agent-runs/{runId}/tasks")
@RequiredArgsConstructor
public class AgentTaskController {

    private final AgentTaskService agentTaskService;

    @PostMapping
    public ResponseEntity<AgentTask> createTask(
            @PathVariable Long projectId,
            @PathVariable Long runId,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam Integer taskOrder
    ) {
        return ResponseEntity.ok(
                agentTaskService.createTask(
                        runId,
                        title,
                        description,
                        taskOrder
                )
        );
    }

    @GetMapping
    public ResponseEntity<List<AgentTask>> getTasks(
            @PathVariable Long projectId,
            @PathVariable Long runId
    ) {
        return ResponseEntity.ok(
                agentTaskService.getTasks(runId)
        );
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<AgentTask> getTask(
            @PathVariable Long projectId,
            @PathVariable Long runId,
            @PathVariable Long taskId
    ) {
        return ResponseEntity.ok(
                agentTaskService.getTask(taskId)
        );
    }

    @PostMapping("/{taskId}/start")
    public ResponseEntity<AgentTask> startTask(
            @PathVariable Long projectId,
            @PathVariable Long runId,
            @PathVariable Long taskId
    ) {
        return ResponseEntity.ok(
                agentTaskService.startTask(taskId)
        );
    }

    @PostMapping("/{taskId}/complete")
    public ResponseEntity<AgentTask> completeTask(
            @PathVariable Long projectId,
            @PathVariable Long runId,
            @PathVariable Long taskId
    ) {
        return ResponseEntity.ok(
                agentTaskService.completeTask(taskId)
        );
    }

    @PostMapping("/{taskId}/fail")
    public ResponseEntity<AgentTask> failTask(
            @PathVariable Long projectId,
            @PathVariable Long runId,
            @PathVariable Long taskId,
            @RequestParam String errorMessage
    ) {
        return ResponseEntity.ok(
                agentTaskService.failTask(
                        taskId,
                        errorMessage
                )
        );
    }
}