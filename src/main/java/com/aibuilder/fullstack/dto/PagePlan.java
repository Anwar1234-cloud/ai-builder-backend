package com.aibuilder.fullstack.dto;

import java.util.List;

public record PagePlan(
        String name,
        String route,
        String description,
        boolean authenticationRequired,
        List<String> requiredRoles,
        List<String> apiPaths
) {
}