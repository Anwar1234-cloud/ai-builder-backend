
package com.aibuilder.fullstack.validation;

import com.aibuilder.fullstack.dto.FullStackPlan;
import com.aibuilder.fullstack.runtime.GeneratedAppDatabaseService;
import com.aibuilder.fullstack.runtime.GeneratedAppRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FullStackValidationOrchestrator {

    private final GeneratedAppDatabaseService databaseService;
    private final GeneratedAppRuntimeService runtimeService;
    private final FullStackFunctionalValidationService validator;

    public FullStackFunctionalValidationService.ValidationReport validate(
            Long projectId,
            Path backendDirectory,
            FullStackPlan plan
    ) throws Exception {

        if (projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("Invalid project ID");
        }

        if (backendDirectory == null
                || !Files.isDirectory(backendDirectory)) {
            throw new IllegalArgumentException(
                    "Generated backend directory does not exist"
            );
        }

        if (plan == null) {
            throw new IllegalArgumentException(
                    "Full-stack plan is required"
            );
        }

        String databaseUsername =
                System.getenv("GENERATED_APP_DB_USER");

        String databasePassword =
                System.getenv("GENERATED_APP_DB_PASSWORD");

        if (databaseUsername == null
                || databaseUsername.isBlank()
                || databasePassword == null
                || databasePassword.isBlank()) {
            throw new IllegalStateException(
                    "Generated database credentials are missing"
            );
        }

        String testRunId =
                "test_" + UUID.randomUUID()
                        .toString().replace("-", "");

        String testDatabase = null;

        try {
            testDatabase =
                    databaseService.provisionTestDatabase(projectId);

            runtimeService.startTestRuntime(
                    projectId,
                    testRunId,
                    backendDirectory,
                    testDatabase,
                    databaseUsername,
                    databasePassword
            );

            boolean ready = runtimeService.waitUntilTestReady(
                    testRunId,
                    Duration.ofSeconds(60)
            );

            if (!ready) {
                throw new IllegalStateException(
                        "Generated test backend failed to become ready"
                );
            }

            FullStackFunctionalValidationService.ValidationReport report =
                    validator.validate(
                            projectId,
                            testRunId,
                            plan
                    );

            if (report.passed()) {
                return report;
            }

            String runtimeLogs =
                    runtimeService.getTestRuntimeDiagnostics(testRunId);

            String diagnosticCategory =
                    classifyRuntimeFailure(runtimeLogs);

            List<FullStackFunctionalValidationService.TestResult> results =
                    new ArrayList<>();

            for (var result : report.results()) {

                if (result.passed()) {
                    results.add(result);
                    continue;
                }

                String message = result.message();

                if (message != null
                        && message.contains("received HTTP")) {

                    message += " | Runtime diagnostic: "
                            + diagnosticCategory;
                }

                results.add(
                        new FullStackFunctionalValidationService.TestResult(
                                result.name(),
                                false,
                                message
                        )
                );
            }

            return new FullStackFunctionalValidationService.ValidationReport(
                    false,
                    results
            );

        } finally {

            // Always stop the test process before dropping its DB.
            runtimeService.stopTestRuntime(testRunId);

            if (testDatabase != null) {
                databaseService.dropTestDatabase(testDatabase);
            }
        }
    }
    private String classifyRuntimeFailure(String runtimeLogs) {

        if (runtimeLogs == null || runtimeLogs.isBlank()) {
            return "NO_RUNTIME_LOGS";
        }

        String logs = runtimeLogs.toLowerCase(Locale.ROOT);

        if (logs.contains("bad credentials")
                || logs.contains("authentication failed")
                || logs.contains("invalid jwt")
                || logs.contains("expired jwt")) {
            return "AUTHENTICATION_ERROR_IN_LOGS";
        }

        if (logs.contains("access denied")
                || logs.contains("authorization denied")) {
            return "ACCESS_DENIED_IN_LOGS";
        }

        if (logs.contains("constraintviolationexception")
                || logs.contains("dataintegrityviolationexception")) {
            return "DATABASE_CONSTRAINT_ERROR_IN_LOGS";
        }

        if (logs.contains("sqlgrammarexception")
                || logs.contains("relation does not exist")
                || logs.contains("column does not exist")) {
            return "DATABASE_SCHEMA_ERROR_IN_LOGS";
        }

        if (logs.contains("nullpointerexception")) {
            return "NULL_POINTER_ERROR_IN_LOGS";
        }

        if (logs.contains("exception")
                || logs.contains("error")) {
            return "SERVER_EXCEPTION_IN_LOGS";
        }

        return "NO_RECOGNIZED_RUNTIME_ERROR";
    }
}
