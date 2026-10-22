package com.aibuilder.fullstack.dto;

import java.util.List;

public record FullStackPlan(
        String applicationName,
        String description,
        String frontendFramework,
        String backendFramework,
        String database,
        boolean authenticationRequired,
        List<RolePlan> roles,
        List<EntityPlan> entities,
        List<RelationshipPlan> relationships,
        List<ApiPlan> apis,
        List<PagePlan> pages,
        List<FeaturePlan> features,
        List<FunctionalTestPlan> functionalTests
) {
}