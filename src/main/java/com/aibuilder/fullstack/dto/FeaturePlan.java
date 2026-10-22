package com.aibuilder.fullstack.dto;

import java.util.List;

public record FeaturePlan(
        String name,
        String description,
        List<String> relatedEntities,
        List<String> relatedPages
) {
}