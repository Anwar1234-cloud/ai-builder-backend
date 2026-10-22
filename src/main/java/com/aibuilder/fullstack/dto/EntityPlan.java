package com.aibuilder.fullstack.dto;

import java.util.List;

public record EntityPlan(
        String name,
        String description,
        List<FieldPlan> fields
) {
}