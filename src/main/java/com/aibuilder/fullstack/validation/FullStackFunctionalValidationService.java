
package com.aibuilder.fullstack.validation;

import com.aibuilder.fullstack.dto.FunctionalTestPlan;
import com.aibuilder.fullstack.dto.FullStackPlan;
import com.aibuilder.fullstack.runtime.GeneratedAppRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FullStackFunctionalValidationService {

    private static final Pattern VARIABLE_PATTERN =
            Pattern.compile("\\$\\{([a-zA-Z][a-zA-Z0-9_]*)}");

    private static final Pattern VARIABLE_NAME =
            Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*");

    private static final int MAX_TESTS = 50;

    private final GeneratedAppRuntimeService runtimeService;
    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.builder()
            .requestFactory(
                    new org.springframework.http.client
                            .SimpleClientHttpRequestFactory() {{
                        setConnectTimeout(Duration.ofSeconds(3));
                        setReadTimeout(Duration.ofSeconds(10));
                    }}
            )
            .build();

    public ValidationReport validate(
            Long projectId,
            String testRunId,
            FullStackPlan plan
    ) {

        var app = runtimeService.getTestRunningApp(testRunId);

        if (app == null) {
            throw new IllegalStateException(
                    "Generated test backend is not running"
            );
        }

        if (!app.projectId().equals(projectId)) {
            throw new IllegalArgumentException(
                    "Test runtime belongs to another project"
            );
        }

        if (plan == null
                || plan.functionalTests() == null
                || plan.functionalTests().isEmpty()) {

            return new ValidationReport(
                    false,
                    List.of(new TestResult(
                            "Test coverage",
                            false,
                            "No functional tests were generated"
                    ))
            );
        }

        if (plan.functionalTests().size() > MAX_TESTS) {
            return new ValidationReport(
                    false,
                    List.of(new TestResult(
                            "Test coverage",
                            false,
                            "Too many functional tests"
                    ))
            );
        }

        Map<String, Object> variables = new HashMap<>();
        Map<String, Boolean> completed = new HashMap<>();
        List<TestResult> results = new ArrayList<>();

        for (FunctionalTestPlan test : plan.functionalTests()) {

            if (test == null) {
                results.add(new TestResult(
                        "Unknown test",
                        false,
                        "Null test definition"
                ));
                continue;
            }

            String name = test.name();

            if (name == null || name.isBlank()) {
                results.add(new TestResult(
                        "Unnamed test",
                        false,
                        "Test name is missing"
                ));
                continue;
            }

            if (completed.containsKey(name)) {
                results.add(new TestResult(
                        name,
                        false,
                        "Duplicate test name"
                ));
                continue;
            }

            boolean dependenciesPassed = true;

            if (test.dependsOn() != null) {
                for (String dependency : test.dependsOn()) {
                    if (!Boolean.TRUE.equals(
                            completed.get(dependency))) {
                        dependenciesPassed = false;
                        break;
                    }
                }
            }

            if (!dependenciesPassed) {
                results.add(new TestResult(
                        name,
                        false,
                        "Required dependency has not passed: "
                                + test.dependsOn().stream()
                                .filter(dependency ->
                                        !Boolean.TRUE.equals(completed.get(dependency)))
                                .toList()
                ));
                completed.put(name, false);
                continue;
            }

            TestResult result = executeTest(
                    app.port(),
                    test,
                    variables
            );

            results.add(result);
            completed.put(name, result.passed());
        }

        boolean passed = results.stream()
                .allMatch(TestResult::passed);

        return new ValidationReport(passed, results);
    }

    private TestResult executeTest(
            int port,
            FunctionalTestPlan test,
            Map<String, Object> variables
    ) {

        String name = test.name();

        try {
            if (test.method() == null) {
                throw new IllegalArgumentException(
                        "HTTP method is missing"
                );
            }

            HttpMethod method = HttpMethod.valueOf(
                    test.method().toUpperCase(Locale.ROOT)
            );

            if (!Set.of(
                    HttpMethod.GET,
                    HttpMethod.POST,
                    HttpMethod.PUT,
                    HttpMethod.PATCH,
                    HttpMethod.DELETE
            ).contains(method)) {
                throw new IllegalArgumentException(
                        "Unsupported HTTP method"
                );
            }

            String endpoint = resolveString(
                    test.endpoint(),
                    variables
            );

            validateEndpoint(endpoint);

            String url = "http://127.0.0.1:"
                    + port + endpoint;

            var request = restClient.method(method)
                    .uri(URI.create(url));

            if (test.authenticationRequired()) {

                String tokenVariable =
                        test.authTokenVariable();

                if (tokenVariable == null
                        || tokenVariable.isBlank()) {
                    throw new IllegalStateException(
                            "Authentication token variable missing"
                    );
                }

                Object token = variables.get(tokenVariable);

                if (!(token instanceof String)
                        || ((String) token).isBlank()) {
                    throw new IllegalStateException(
                            "Authentication token not available"
                    );
                }

                request = request.header(
                        "Authorization",
                        "Bearer " + token
                );
            }

            if (test.requestBody() != null) {

                Object resolvedBody = resolveValue(
                        test.requestBody(),
                        variables
                );

                request = request.body(resolvedBody);
            }

            HttpResult response = request.exchange(
                    (req, res) -> {

                        byte[] bodyBytes =
                                res.getBody().readNBytes(1_048_577);

                        if (bodyBytes.length > 1_048_576) {
                            throw new IllegalStateException(
                                    "Response body exceeds 1 MB"
                            );
                        }

                        return new HttpResult(
                                res.getStatusCode().value(),
                                new String(
                                        bodyBytes,
                                        StandardCharsets.UTF_8
                                )
                        );
                    }
            );

            if (response.status() != test.expectedStatus()) {

                String diagnostic = buildHttpFailureDiagnostic(
                        method,
                        endpoint,
                        test,
                        response.status()
                );

                System.err.println(
                        "[FUNCTIONAL-TEST] " + name + " | " + diagnostic
                );

                return new TestResult(
                        name,
                        false,
                        diagnostic
                );
            }

            Map<String, Object> json = null;

            boolean needsJson =
                    test.expectedResponseFields() != null
                            && !test.expectedResponseFields().isEmpty();

            needsJson = needsJson
                    || (test.extractVariables() != null
                    && !test.extractVariables().isEmpty());

            if (needsJson) {
                json = objectMapper.readValue(
                        response.body(),
                        Map.class
                );
            }

            if (test.expectedResponseFields() != null) {

                for (var entry :
                        test.expectedResponseFields().entrySet()) {

                    Object expected = resolveValue(
                            entry.getValue(),
                            variables
                    );

                    Object actual = getJsonPath(
                            json,
                            entry.getKey()
                    );

                    if (!Objects.equals(expected, actual)) {
                        return new TestResult(
                                name,
                                false,
                                "Response mismatch at: "
                                        + entry.getKey()
                        );
                    }
                }
            }

            Map<String, Object> extracted =
                    new HashMap<>();

            if (test.extractVariables() != null) {

                for (var entry :
                        test.extractVariables().entrySet()) {

                    String variableName = entry.getKey();

                    if (variableName == null
                            || !VARIABLE_NAME.matcher(
                            variableName).matches()) {
                        throw new IllegalArgumentException(
                                "Invalid variable name"
                        );
                    }

                    Object value = getJsonPath(
                            json,
                            entry.getValue()
                    );

                    if (value == null) {
                        throw new IllegalStateException(
                                "Cannot extract variable: "
                                        + variableName
                        );
                    }

                    if (variables.containsKey(variableName)) {
                        throw new IllegalStateException(
                                "Variable already exists: "
                                        + variableName
                        );
                    }

                    extracted.put(variableName, value);
                }
            }

            // Commit variables only after all assertions pass.
            variables.putAll(extracted);

            return new TestResult(
                    name,
                    true,
                    "HTTP " + response.status()
            );

        } catch (Exception exception) {

            // Do not include response bodies, tokens or credentials.
            return new TestResult(
                    name,
                    false,
                    exception instanceof IllegalArgumentException
                            || exception instanceof IllegalStateException
                            ? safeMessage(exception)
                            : "Request execution failed: "
                            + exception.getClass().getSimpleName()
            );
        }
    }

    private String buildHttpFailureDiagnostic(
            HttpMethod method,
            String endpoint,
            FunctionalTestPlan test,
            int actualStatus
    ) {
        StringBuilder diagnostic = new StringBuilder();

        diagnostic.append("Endpoint: ")
                .append(method.name())
                .append(" ")
                .append(endpoint)
                .append(" | Expected HTTP ")
                .append(test.expectedStatus())
                .append(", received HTTP ")
                .append(actualStatus)
                .append(" | Authentication required: ")
                .append(test.authenticationRequired());

        if (actualStatus == 401) {
            diagnostic.append(
                    " | Category: AUTHENTICATION_FAILURE"
            );
        } else if (actualStatus == 403) {
            diagnostic.append(
                    " | Category: AUTHORIZATION_FAILURE"
            );
        } else if (actualStatus == 400 || actualStatus == 422) {
            diagnostic.append(
                    " | Category: REQUEST_VALIDATION_FAILURE"
            );
        } else if (actualStatus == 404) {
            diagnostic.append(
                    " | Category: ENDPOINT_NOT_FOUND"
            );
        } else if (actualStatus >= 500) {
            diagnostic.append(
                    " | Category: SERVER_ERROR"
            );
        } else {
            diagnostic.append(
                    " | Category: UNEXPECTED_HTTP_STATUS"
            );
        }

        return diagnostic.toString();
    }

    private void validateEndpoint(String endpoint) {

        if (endpoint == null
                || !endpoint.startsWith("/api/")
                || endpoint.contains("://")
                || endpoint.contains("\\")
                || endpoint.contains("#")
                || endpoint.contains("//")
                || endpoint.contains("..")
                || endpoint.contains("\r")
                || endpoint.contains("\n")) {

            throw new IllegalArgumentException(
                    "Invalid API endpoint"
            );
        }
    }

    private String resolveString(
            String input,
            Map<String, Object> variables
    ) {

        if (input == null) {
            return null;
        }

        Matcher matcher =
                VARIABLE_PATTERN.matcher(input);

        StringBuffer result = new StringBuffer();

        while (matcher.find()) {

            String key = matcher.group(1);

            if (!variables.containsKey(key)) {
                throw new IllegalStateException(
                        "Undefined variable: " + key
                );
            }

            Object value = variables.get(key);

            if (value == null
                    || value instanceof Map
                    || value instanceof List) {
                throw new IllegalStateException(
                        "Variable is not a scalar: " + key
                );
            }

            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(
                            String.valueOf(value)
                    )
            );
        }

        matcher.appendTail(result);

        return result.toString();
    }

    private Object resolveValue(
            Object value,
            Map<String, Object> variables
    ) {

        if (value instanceof String text) {

            Matcher matcher =
                    VARIABLE_PATTERN.matcher(text);

            if (matcher.matches()) {

                String key = matcher.group(1);

                if (!variables.containsKey(key)) {
                    throw new IllegalStateException(
                            "Undefined variable: " + key
                    );
                }

                return variables.get(key);
            }

            return resolveString(text, variables);
        }

        if (value instanceof Map<?, ?> map) {

            Map<String, Object> resolved =
                    new LinkedHashMap<>();

            for (var entry : map.entrySet()) {

                resolved.put(
                        String.valueOf(entry.getKey()),
                        resolveValue(
                                entry.getValue(),
                                variables
                        )
                );
            }

            return resolved;
        }

        if (value instanceof List<?> list) {

            List<Object> resolved =
                    new ArrayList<>();

            for (Object item : list) {
                resolved.add(
                        resolveValue(item, variables)
                );
            }

            return resolved;
        }

        return value;
    }

    private Object getJsonPath(
            Map<String, Object> json,
            String path
    ) {

        if (json == null
                || path == null
                || path.isBlank()) {
            return null;
        }

        Object current = json;

        for (String segment : path.split("\\.")) {

            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }

            current = map.get(segment);

            if (current == null) {
                return null;
            }
        }

        return current;
    }

    private String safeMessage(Exception exception) {

        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        // Avoid returning arbitrary values that might
        // contain generated test credentials.
        if (message.length() > 200) {
            return message.substring(0, 200);
        }

        return message;
    }

    private record HttpResult(
            int status,
            String body
    ) {}

    public record TestResult(
            String name,
            boolean passed,
            String message
    ) {}

    public record ValidationReport(
            boolean passed,
            List<TestResult> results
    ) {}
}
