package com.aibuilder.fullstack.dto;

import java.util.List;

public record RolePlan(
        String name,
        String description,
        List<String> permissions
) {
}