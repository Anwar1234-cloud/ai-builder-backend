package com.aibuilder.fullstack.dto;

public record FieldPlan(
        String name,
        String type,
        boolean required,
        boolean unique,
        String description
) {
}