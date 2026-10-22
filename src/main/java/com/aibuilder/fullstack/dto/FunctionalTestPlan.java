
package com.aibuilder.fullstack.dto;

import java.util.List;
import java.util.Map;

public record FunctionalTestPlan(
        String name,
        String description,
        String method,
        String endpoint,
        Map<String, Object> requestBody,
        int expectedStatus,
        boolean authenticationRequired,
        String requiredRole,
        Map<String, Object> expectedResponseFields,

        // Names of tests that must execute first
        List<String> dependsOn,

        // Extract values from JSON responses
        // Example: {"taskId": "id"}
        Map<String, String> extractVariables,

        // Name of a variable containing an auth token
        String authTokenVariable
) {
}
