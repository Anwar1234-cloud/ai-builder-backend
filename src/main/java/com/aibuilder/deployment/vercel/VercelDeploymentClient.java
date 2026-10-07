package com.aibuilder.deployment.vercel;

import com.aibuilder.deployment.exception.DeploymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class VercelDeploymentClient {

    private final VercelProperties properties;

    private final VercelPrebuiltPackager
            prebuiltPackager;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient =
            HttpClient.newBuilder()
                    .followRedirects(
                            HttpClient.Redirect.NORMAL
                    )
                    .connectTimeout(
                            Duration.ofSeconds(30)
                    )
                    .build();


    public VercelDeploymentResult deploy(
            Path distDirectory,
            String projectName
    ) {

        validateConfiguration();

        /*
         * Convert our already-built dist/ directory into:
         *
         * .vercel/output/
         *     config.json
         *     static/
         *
         * No Vite build should happen after this.
         */
        Path workspace =
                prebuiltPackager.packageBuild(
                        distDirectory
                );

        String sanitizedProjectName =
                sanitizeProjectName(
                        projectName
                );

        try {

            List<String> command =
                    new ArrayList<>();

            /*
             * Windows:
             *
             * npx.cmd vercel deploy ...
             */
            command.add("npx.cmd");
            command.add("vercel");
            command.add("deploy");

            /*
             * Critical:
             *
             * tells Vercel that .vercel/output already
             * contains the finished build.
             */
            command.add("--prebuilt");

            /*
             * Production deployment.
             */
            command.add("--prod");

            /*
             * No interactive prompts.
             */
            command.add("--yes");
            command.add("--json");

            command.add("--token");
            command.add(properties.getToken());

            /*
             * Explicit project name for AI Builder projects.
             */
            command.add("--name");
            command.add(sanitizedProjectName);

            if (hasTeamId()) {

                command.add("--scope");
                command.add(
                        properties.getTeamId()
                );
            }

            ProcessBuilder processBuilder =
                    new ProcessBuilder(command);

            processBuilder.directory(
                    workspace.toFile()
            );

            processBuilder.redirectErrorStream(
                    true
            );

            Process process =
                    processBuilder.start();

            StringBuilder output =
                    new StringBuilder();

            try (
                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            process.getInputStream(),
                                            StandardCharsets.UTF_8
                                    )
                            )
            ) {

                String line;

                while ((line = reader.readLine())
                        != null) {

                    output.append(line)
                            .append(
                                    System.lineSeparator()
                            );
                }
            }

            boolean finished =
                    process.waitFor(
                            3,
                            java.util.concurrent.TimeUnit.MINUTES
                    );

            if (!finished) {

                process.destroyForcibly();

                throw new DeploymentException(
                        "Vercel prebuilt deployment timed out."
                );
            }

            int exitCode =
                    process.exitValue();

            String processOutput =
                    output.toString();

            if (exitCode != 0) {

                throw new DeploymentException(
                        "Vercel prebuilt deployment failed. "
                                + "Exit code: "
                                + exitCode
                                + ". Output: "
                                + processOutput
                );
            }

            VercelDeploymentResult result =
                    parseCliDeploymentResult(
                            processOutput
                    );

            return result;

        } catch (DeploymentException e) {

            throw e;

        } catch (Exception e) {

            throw new DeploymentException(
                    "Vercel prebuilt deployment failed: "
                            + (
                            e.getMessage() != null
                                    ? e.getMessage()
                                    : e.getClass().getName()
                    ),
                    e
            );
        }
    }

    private VercelDeploymentResult parseCliDeploymentResult(
            String output
    ) {

        if (output == null || output.isBlank()) {
            throw new DeploymentException(
                    "Vercel CLI returned empty deployment output."
            );
        }

        try {

            /*
             * Vercel CLI prints normal status messages first,
             * followed by pretty-printed multi-line JSON.
             *
             * Find the JSON object by locating a block that
             * contains the deployment "id".
             */
            int jsonStart = output.indexOf("{");

            while (jsonStart >= 0) {

                String candidate =
                        output.substring(jsonStart).trim();

                try {

                    JsonNode json =
                            objectMapper.readTree(candidate);

                    String id =
                            textValue(json, "id");

                    String url =
                            textValue(json, "url");

                    if (id != null
                            && !id.isBlank()
                            && url != null
                            && !url.isBlank()) {

                        if (!url.startsWith("http://")
                                && !url.startsWith("https://")) {

                            url = "https://" + url;
                        }

                        return new VercelDeploymentResult(
                                id,
                                url
                        );
                    }

                } catch (Exception ignored) {

                    /*
                     * This opening brace wasn't the beginning
                     * of the deployment JSON.
                     */
                }

                jsonStart =
                        output.indexOf(
                                "{",
                                jsonStart + 1
                        );
            }

            throw new DeploymentException(
                    "Vercel CLI completed successfully but "
                            + "deployment JSON could not be parsed."
            );

        } catch (DeploymentException e) {

            throw e;

        } catch (Exception e) {

            throw new DeploymentException(
                    "Failed to parse Vercel CLI deployment output: "
                            + (
                            e.getMessage() != null
                                    ? e.getMessage()
                                    : e.getClass().getName()
                    ),
                    e
            );
        }
    }


    public VercelDeploymentStatus getDeploymentStatus(
            String deploymentId
    ) {

        if (deploymentId == null
                || deploymentId.isBlank()) {

            throw new DeploymentException(
                    "Vercel deployment ID is required."
            );
        }

        try {

            String url =
                    properties.getApiBaseUrl()
                            + "/v13/deployments/"
                            + URLEncoder.encode(
                            deploymentId,
                            StandardCharsets.UTF_8
                    );

            if (hasTeamId()) {

                url += "?teamId="
                        + URLEncoder.encode(
                        properties.getTeamId(),
                        StandardCharsets.UTF_8
                );
            }

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(url)
                            )
                            .header(
                                    "Authorization",
                                    "Bearer "
                                            + properties.getToken()
                            )
                            .GET()
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers
                                    .ofString()
                    );

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {

                throw new DeploymentException(
                        "Failed to read Vercel deployment status: "
                                + response.body()
                );
            }

            JsonNode json =
                    objectMapper.readTree(
                            response.body()
                    );

            String state =
                    textValue(
                            json,
                            "state"
                    );

            String readyState =
                    textValue(
                            json,
                            "readyState"
                    );

            String deploymentUrl =
                    textValue(
                            json,
                            "url"
                    );

            if (deploymentUrl != null
                    && !deploymentUrl.startsWith(
                    "http://"
            )
                    && !deploymentUrl.startsWith(
                    "https://"
            )) {

                deploymentUrl =
                        "https://"
                                + deploymentUrl;
            }

            String errorMessage =
                    extractErrorMessage(
                            json
                    );

            return new VercelDeploymentStatus(
                    state,
                    readyState,
                    deploymentUrl,
                    errorMessage
            );

        } catch (DeploymentException e) {

            throw e;

        } catch (Exception e) {

            throw new DeploymentException(
                    "Failed to check Vercel deployment status: "
                            + (
                            e.getMessage() != null
                                    ? e.getMessage()
                                    : e.getClass().getName()
                    ),
                    e
            );
        }
    }


    public VercelDeploymentStatus waitForDeployment(
            String deploymentId
    ) {

        final int maxAttempts = 30;
        final long delayMillis = 2000L;

        VercelDeploymentStatus lastStatus =
                null;

        for (
                int attempt = 1;
                attempt <= maxAttempts;
                attempt++
        ) {

            lastStatus =
                    getDeploymentStatus(
                            deploymentId
                    );

            if (lastStatus.isReady()) {

                return lastStatus;
            }

            if (lastStatus.isFailed()) {

                throw new DeploymentException(
                        "Vercel deployment entered ERROR state. "
                                + "state="
                                + lastStatus.state()
                                + ", readyState="
                                + lastStatus.readyState()
                                + ", error="
                                + (
                                lastStatus.errorMessage()
                                        != null
                                        ? lastStatus.errorMessage()
                                        : "No error message returned "
                                        + "by deployment status API"
                        )
                );
            }

            if (attempt < maxAttempts) {

                try {

                    Thread.sleep(
                            delayMillis
                    );

                } catch (
                        InterruptedException e
                ) {

                    Thread.currentThread()
                            .interrupt();

                    throw new DeploymentException(
                            "Interrupted while waiting "
                                    + "for Vercel deployment.",
                            e
                    );
                }
            }
        }

        throw new DeploymentException(
                "Timed out waiting for Vercel deployment. "
                        + "Last state: "
                        + (
                        lastStatus != null
                                ? lastStatus.readyState()
                                : "UNKNOWN"
                )
        );
    }


    private String extractDeploymentUrl(
            String output
    ) {

        if (output == null
                || output.isBlank()) {

            return null;
        }

        String[] lines =
                output.split("\\R");

        for (
                int i = lines.length - 1;
                i >= 0;
                i--
        ) {

            String line =
                    lines[i].trim();

            int httpsIndex =
                    line.indexOf(
                            "https://"
                    );

            if (httpsIndex < 0) {
                continue;
            }

            String candidate =
                    line.substring(
                            httpsIndex
                    ).trim();

            int whitespace =
                    candidate.indexOf(' ');

            if (whitespace > 0) {

                candidate =
                        candidate.substring(
                                0,
                                whitespace
                        );
            }

            if (candidate.contains(
                    ".vercel.app"
            )) {

                return candidate;
            }
        }

        return null;
    }


    private String extractErrorMessage(
            JsonNode json
    ) {

        JsonNode error =
                json.get("error");

        if (error != null
                && !error.isNull()) {

            if (error.has("message")) {

                return error.get(
                        "message"
                ).asText();
            }

            return error.toString();
        }

        return null;
    }


    private String textValue(
            JsonNode json,
            String field
    ) {

        JsonNode value =
                json.get(field);

        if (value == null
                || value.isNull()) {

            return null;
        }

        return value.asText();
    }


    private void validateConfiguration() {

        if (properties.getToken() == null
                || properties.getToken()
                .isBlank()) {

            throw new DeploymentException(
                    "VERCEL_TOKEN is not configured."
            );
        }
    }


    private boolean hasTeamId() {

        return properties.getTeamId()
                != null
                && !properties.getTeamId()
                .isBlank();
    }


    private String sanitizeProjectName(
            String value
    ) {

        String name =
                value == null
                        ? "ai-builder-project"
                        : value
                        .toLowerCase()
                        .replaceAll(
                                "[^a-z0-9._-]+",
                                "-"
                        )
                        .replaceAll(
                                "^-+|-+$",
                                ""
                        );

        if (name.isBlank()) {

            return "ai-builder-project";
        }

        return name;
    }



}