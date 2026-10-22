package com.aibuilder.fullstack.dto;

public record ApiPlan(
        String method,
        String path,
        String purpose,
        String requestEntity,
        String responseEntity,
        boolean authenticationRequired,
        String requiredRole
) {
}