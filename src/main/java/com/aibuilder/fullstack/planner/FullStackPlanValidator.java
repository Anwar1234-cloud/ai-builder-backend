package com.aibuilder.fullstack.planner;

import com.aibuilder.fullstack.dto.*;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class FullStackPlanValidator {

    public void validate(FullStackPlan plan) {

        if (plan == null) {
            throw new IllegalArgumentException(
                    "Full-stack architecture plan cannot be null"
            );
        }

        requireText(plan.applicationName(), "Application name");
        requireText(plan.description(), "Application description");

        if (!"React".equalsIgnoreCase(plan.frontendFramework())) {
            throw new IllegalArgumentException(
                    "Phase 1 supports React frontend only"
            );
        }

        if (!"Spring Boot".equalsIgnoreCase(plan.backendFramework())) {
            throw new IllegalArgumentException(
                    "Phase 1 supports Spring Boot backend only"
            );
        }

        if (!"PostgreSQL".equalsIgnoreCase(plan.database())) {
            throw new IllegalArgumentException(
                    "Phase 1 supports PostgreSQL database only"
            );
        }

        List<EntityPlan> entities = requireList(
                plan.entities(), "Entities"
        );

        List<ApiPlan> apis = requireList(
                plan.apis(), "APIs"
        );

        List<PagePlan> pages = requireList(
                plan.pages(), "Pages"
        );

        List<RolePlan> roles = requireList(
                plan.roles(), "Roles"
        );

        List<RelationshipPlan> relationships = requireList(
                plan.relationships(), "Relationships"
        );

        List<FeaturePlan> features = requireList(
                plan.features(), "Features"
        );

        Set<String> entityNames = new HashSet<>();

        for (EntityPlan entity : entities) {

            if (entity == null) {
                throw new IllegalArgumentException(
                        "Entity cannot be null"
                );
            }

            requireText(entity.name(), "Entity name");

            if (!entityNames.add(entity.name())) {
                throw new IllegalArgumentException(
                        "Duplicate entity: " + entity.name()
                );
            }

            Set<String> fieldNames = new HashSet<>();

            for (FieldPlan field :
                    requireList(entity.fields(), "Entity fields")) {

                if (field == null) {
                    throw new IllegalArgumentException(
                            "Field cannot be null"
                    );
                }

                requireText(field.name(), "Field name");
                requireText(field.type(), "Field type");

                if (!fieldNames.add(field.name())) {
                    throw new IllegalArgumentException(
                            "Duplicate field in "
                                    + entity.name()
                                    + ": "
                                    + field.name()
                    );
                }
            }
        }

        Set<String> roleNames = new HashSet<>();

        for (RolePlan role : roles) {

            if (role == null) {
                throw new IllegalArgumentException(
                        "Role cannot be null"
                );
            }

            requireText(role.name(), "Role name");

            if (!roleNames.add(role.name())) {
                throw new IllegalArgumentException(
                        "Duplicate role: " + role.name()
                );
            }

            requireList(role.permissions(), "Role permissions");
        }

        if (plan.authenticationRequired() && roles.isEmpty()) {
            throw new IllegalArgumentException(
                    "Authentication requires at least one role"
            );
        }

        Set<String> apiKeys = new HashSet<>();

        for (ApiPlan api : apis) {

            if (api == null) {
                throw new IllegalArgumentException(
                        "API cannot be null"
                );
            }

            requireText(api.method(), "API method");
            requireText(api.path(), "API path");

            String method = api.method().toUpperCase();

            if (!Set.of(
                    "GET", "POST", "PUT", "PATCH", "DELETE"
            ).contains(method)) {
                throw new IllegalArgumentException(
                        "Unsupported HTTP method: " + api.method()
                );
            }

            if (!api.path().startsWith("/api/")) {
                throw new IllegalArgumentException(
                        "API path must start with /api/: "
                                + api.path()
                );
            }

            if (!apiKeys.add(method + " " + api.path())) {
                throw new IllegalArgumentException(
                        "Duplicate API: "
                                + method + " " + api.path()
                );
            }

            if (api.requiredRole() != null
                    && !api.requiredRole().isBlank()
                    && !roleNames.contains(api.requiredRole())) {

                throw new IllegalArgumentException(
                        "Unknown API role: " + api.requiredRole()
                );
            }

            if (api.requiredRole() != null
                    && !api.requiredRole().isBlank()
                    && !api.authenticationRequired()) {

                throw new IllegalArgumentException(
                        "Role-protected API must require authentication"
                );
            }
        }

        Set<String> pageRoutes = new HashSet<>();

        for (PagePlan page : pages) {

            if (page == null) {
                throw new IllegalArgumentException(
                        "Page cannot be null"
                );
            }

            requireText(page.name(), "Page name");
            requireText(page.route(), "Page route");

            if (!page.route().startsWith("/")) {
                throw new IllegalArgumentException(
                        "Page route must start with /"
                );
            }

            if (!pageRoutes.add(page.route())) {
                throw new IllegalArgumentException(
                        "Duplicate page route: " + page.route()
                );
            }

            for (String role :
                    requireList(page.requiredRoles(), "Page roles")) {

                if (!roleNames.contains(role)) {
                    throw new IllegalArgumentException(
                            "Unknown page role: " + role
                    );
                }

                if (!page.authenticationRequired()) {
                    throw new IllegalArgumentException(
                            "Role-protected page must require authentication"
                    );
                }
            }

            requireList(page.apiPaths(), "Page API paths");
        }

        for (RelationshipPlan relationship : relationships) {

            if (relationship == null) {
                throw new IllegalArgumentException(
                        "Relationship cannot be null"
                );
            }

            requireText(
                    relationship.sourceEntity(),
                    "Relationship source"
            );

            requireText(
                    relationship.targetEntity(),
                    "Relationship target"
            );

            requireText(
                    relationship.relationshipType(),
                    "Relationship type"
            );

            requireText(
                    relationship.fieldName(),
                    "Relationship field"
            );

            if (!entityNames.contains(relationship.sourceEntity())
                    || !entityNames.contains(
                    relationship.targetEntity())) {

                throw new IllegalArgumentException(
                        "Relationship references unknown entity"
                );
            }

            if (!Set.of(
                    "ONE_TO_ONE",
                    "ONE_TO_MANY",
                    "MANY_TO_MANY"
            ).contains(relationship.relationshipType())) {

                throw new IllegalArgumentException(
                        "Unsupported relationship type: "
                                + relationship.relationshipType()
                );
            }
        }

        for (FeaturePlan feature : features) {

            if (feature == null) {
                throw new IllegalArgumentException(
                        "Feature cannot be null"
                );
            }

            requireText(feature.name(), "Feature name");

            for (String entityName :
                    requireList(
                            feature.relatedEntities(),
                            "Feature entities")) {

                if (!entityNames.contains(entityName)) {
                    throw new IllegalArgumentException(
                            "Feature references unknown entity: "
                                    + entityName
                    );
                }
            }

            for (String pageName :
                    requireList(
                            feature.relatedPages(),
                            "Feature pages")) {

                boolean exists = pages.stream()
                        .anyMatch(page ->
                                page.name().equals(pageName));

                if (!exists) {
                    throw new IllegalArgumentException(
                            "Feature references unknown page: "
                                    + pageName
                    );
                }
            }
        }
    }

    private void requireText(String value, String field) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " is required"
            );
        }
    }

    private <T> List<T> requireList(
            List<T> values,
            String field
    ) {

        if (values == null) {
            throw new IllegalArgumentException(
                    field + " cannot be null"
            );
        }

        return values;
    }
}