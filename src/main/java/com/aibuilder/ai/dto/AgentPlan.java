package com.aibuilder.ai.dto;

import java.util.List;

public record AgentPlan(
        List<PlannedTask> tasks
) {

    public record PlannedTask(
            String title,
            String description,
            Integer taskOrder
    ) {
    }
}