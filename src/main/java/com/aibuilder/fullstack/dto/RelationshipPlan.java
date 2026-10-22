package com.aibuilder.fullstack.dto;

public record RelationshipPlan(
        String sourceEntity,
        String targetEntity,
        String relationshipType,
        String fieldName
) {
}