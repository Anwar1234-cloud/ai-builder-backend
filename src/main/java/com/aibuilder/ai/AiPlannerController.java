package com.aibuilder.ai;

import com.aibuilder.ai.dto.AgentPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/planner")
@RequiredArgsConstructor
public class AiPlannerController {

    private final AiPlannerService aiPlannerService;

    @PostMapping
    public ResponseEntity<AgentPlan> createPlan(
            @PathVariable Long projectId,
            @RequestBody PlannerRequest request
    ) {

        return ResponseEntity.ok(
                aiPlannerService.createPlan(
                        projectId,
                        request.userRequest()
                )
        );
    }

    public record PlannerRequest(
            String userRequest
    ) {
    }
}