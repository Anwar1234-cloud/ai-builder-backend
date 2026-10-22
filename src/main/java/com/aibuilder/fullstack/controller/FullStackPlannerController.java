package com.aibuilder.fullstack.controller;

import com.aibuilder.fullstack.dto.FullStackPlan;
import com.aibuilder.fullstack.planner.FullStackPlannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/fullstack")
@RequiredArgsConstructor
public class FullStackPlannerController {

    private final FullStackPlannerService plannerService;

    @PostMapping("/plan")
    public FullStackPlan generatePlan(
            @PathVariable Long projectId,
            @RequestBody FullStackPlanRequest request
    ) {
        return plannerService.createPlan(request.prompt());
    }

    public record FullStackPlanRequest(String prompt) {
    }
}