
package com.aibuilder.deployment.railway;

import com.aibuilder.deployment.exception.DeploymentException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;


@Service
public class RailwayCliDeploymentService {

    private static final Duration UPLOAD_TIMEOUT =
            Duration.ofMinutes(10);

    private final ConcurrentHashMap<String, ReentrantLock> deploymentLocks =
            new ConcurrentHashMap<>();

    public String deployBackend(
            Path backendDirectory,
            String projectId,
            String serviceId,
            String environmentId
    ) {

        if (backendDirectory == null
                || !Files.isDirectory(backendDirectory)) {
            throw new DeploymentException(
                    "Generated backend directory does not exist."
            );
        }

        if (!Files.isRegularFile(
                backendDirectory.resolve("pom.xml"))) {
            throw new DeploymentException(
                    "Generated backend pom.xml not found."
            );
        }

        validateId(projectId, "Railway project ID");
        validateId(serviceId, "Railway service ID");
        validateId(environmentId, "Railway environment ID");


        String railwayExecutable = System.getProperty("os.name")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("win")
                ? "railway.cmd"
                : "railway";

        List<String> command = List.of(
                railwayExecutable,
                "up",
                backendDirectory.toAbsolutePath().toString(),
                "--project", projectId,
                "--service", serviceId,
                "--environment", environmentId,
                "--detach",
                "--yes"
        );


        try {
            ProcessBuilder builder = new ProcessBuilder(command);

            builder.directory(backendDirectory.toFile());
            builder.redirectErrorStream(true);

            // Never expose Railway API credentials to generated builds.
            builder.environment().remove("RAILWAY_TOKEN");
            builder.environment().remove("RAILWAY_API_TOKEN");

            Process process = builder.start();

            Path logFile = Files.createTempFile(
                    "railway-upload-",
                    ".log"
            );

            try {

                Thread outputReader = Thread.ofVirtual().start(() -> {
                    try (
                            var input = process.getInputStream();
                            var output = Files.newOutputStream(logFile)
                    ) {
                        input.transferTo(output);
                    } catch (IOException ignored) {
                        // Process exit status will be checked below.
                    }
                });

                boolean completed = process.waitFor(
                        UPLOAD_TIMEOUT.toMillis(),
                        TimeUnit.MILLISECONDS
                );

                if (!completed) {
                    process.destroyForcibly();
                    process.waitFor();

                    throw new DeploymentException(
                            "Railway backend upload timed out."
                    );
                }

                outputReader.join();

                String output = Files.readString(logFile);

                if (process.exitValue() != 0) {
                    throw new DeploymentException(
                            "Railway backend upload failed. Exit code: "
                                    + process.exitValue()
                    );
                }

                return output;

            } finally {
                Files.deleteIfExists(logFile);
            }

        } catch (IOException e) {
            throw new DeploymentException(
                    "Failed to execute Railway CLI: "
                            + e.getMessage()
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new DeploymentException(
                    "Railway CLI execution interrupted."
            );
        }
    }


    public String listDeployments(
            String projectId,
            String serviceId,
            String environmentId
    ) {
        validateId(projectId, "Railway project ID");
        validateId(serviceId, "Railway service ID");
        validateId(environmentId, "Railway environment ID");

        String executable = System.getProperty("os.name")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("win")
                ? "railway.cmd"
                : "railway";

        List<String> command = List.of(
                executable,
                "deployment",
                "list",
                "--project", projectId,
                "--service", serviceId,
                "--environment", environmentId,
                "--limit", "20",
                "--json"
        );

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);

            Process process = builder.start();

            java.util.concurrent.CompletableFuture<String> outputFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        try {
                            return new String(
                                    process.getInputStream().readAllBytes(),
                                    java.nio.charset.StandardCharsets.UTF_8
                            );
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    });

            boolean completed = process.waitFor(30, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                process.waitFor();

                throw new DeploymentException(
                        "Railway deployment listing timed out."
                );
            }

            String output = outputFuture.join();

            if (process.exitValue() != 0) {
                throw new DeploymentException(
                        "Railway deployment listing failed."
                );
            }

            return output;

        } catch (IOException e) {
            throw new DeploymentException(
                    "Failed to execute Railway CLI: " + e.getMessage()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new DeploymentException(
                    "Railway deployment listing interrupted."
            );
        }
    }


    public java.util.Set<String> getDeploymentIds(
            String projectId,
            String serviceId,
            String environmentId
    ) {
        String json = listDeployments(
                projectId,
                serviceId,
                environmentId
        );

        try {
            tools.jackson.databind.ObjectMapper mapper =
                    new tools.jackson.databind.ObjectMapper();

            tools.jackson.databind.JsonNode deployments =
                    mapper.readTree(json);

            if (!deployments.isArray()) {
                throw new DeploymentException(
                        "Invalid Railway deployment list response."
                );
            }

            java.util.Set<String> ids = new java.util.HashSet<>();

            for (tools.jackson.databind.JsonNode deployment : deployments) {
                String id = deployment.path("id").asText();

                if (id == null || id.isBlank()) {
                    throw new DeploymentException(
                            "Railway deployment response contains a missing ID."
                    );
                }

                ids.add(id);
            }

            return ids;

        } catch (DeploymentException e) {
            throw e;
        } catch (Exception e) {
            throw new DeploymentException(
                    "Failed to parse Railway deployment list: "
                            + e.getMessage()
            );
        }
    }


    public String findNewDeploymentId(
            String projectId,
            String serviceId,
            String environmentId,
            java.util.Set<String> previousDeploymentIds
    ) {
        if (previousDeploymentIds == null) {
            throw new DeploymentException(
                    "Previous Railway deployment IDs are required."
            );
        }


        java.util.Set<String> currentDeploymentIds =
                new java.util.HashSet<>(
                        getDeploymentIds(
                                projectId,
                                serviceId,
                                environmentId
                        )
                );

        currentDeploymentIds.removeAll(previousDeploymentIds);


        if (currentDeploymentIds.isEmpty()) {
            throw new DeploymentException(
                    "No new Railway deployment found."
            );
        }

        if (currentDeploymentIds.size() > 1) {
            throw new DeploymentException(
                    "Multiple new Railway deployments found. "
                            + "Cannot safely identify the uploaded deployment."
            );
        }

        return currentDeploymentIds.iterator().next();
    }


    public String waitForNewDeploymentId(
            String projectId,
            String serviceId,
            String environmentId,
            java.util.Set<String> previousDeploymentIds
    ) {
        if (previousDeploymentIds == null) {
            throw new DeploymentException(
                    "Previous deployment IDs are required."
            );
        }

        long deadline = System.nanoTime()
                + TimeUnit.MINUTES.toNanos(2);

        while (System.nanoTime() < deadline) {
            try {
                return findNewDeploymentId(
                        projectId,
                        serviceId,
                        environmentId,
                        previousDeploymentIds
                );
            } catch (DeploymentException e) {
                if (!"No new Railway deployment found."
                        .equals(e.getMessage())) {
                    throw e;
                }
            }

            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                throw new DeploymentException(
                        "Interrupted while waiting for Railway deployment."
                );
            }
        }

        throw new DeploymentException(
                "Timed out waiting for new Railway deployment ID."
        );
    }


    public String deployBackendAndTrack(
            Path backendDirectory,
            String projectId,
            String serviceId,
            String environmentId,
            RailwayDeploymentClient railwayClient
    ) {
        if (railwayClient == null) {
            throw new DeploymentException(
                    "Railway deployment client is required."
            );
        }

        ReentrantLock lock = getDeploymentLock(
                projectId,
                serviceId,
                environmentId
        );

        if (!lock.tryLock()) {
            throw new DeploymentException(
                    "Another deployment is already running for this Railway service."
            );
        }

        try {

            java.util.Set<String> previousIds =
                    getDeploymentIds(
                            projectId,
                            serviceId,
                            environmentId
                    );


            deployBackend(
                    backendDirectory,
                    projectId,
                    serviceId,
                    environmentId
            );


            String newDeploymentId = waitForNewDeploymentId(
                    projectId,
                    serviceId,
                    environmentId,
                    previousIds
            );



            tools.jackson.databind.JsonNode deployment =
                    railwayClient.waitForDeploymentById(
                            newDeploymentId
                    );

            if (deployment == null || deployment.isMissingNode()) {
                throw new DeploymentException(
                        "Railway deployment response is missing."
                );
            }

            String returnedId = deployment.path("id").asText();
            String status = deployment.path("status").asText();

            if (!newDeploymentId.equals(returnedId)) {
                throw new DeploymentException(
                        "Railway returned an unexpected deployment ID."
                );
            }

            if (!"SUCCESS".equals(status)) {
                throw new DeploymentException(
                        "Railway deployment did not succeed. Status: " + status
                );
            }

            return newDeploymentId;


        } finally {
            lock.unlock();
        }
    }







    public String checkRailwayCli() {

        String executable = System.getProperty("os.name")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("win")
                ? "railway.cmd"
                : "railway";

        ProcessBuilder builder = new ProcessBuilder(
                executable,
                "--version"
        );

        builder.redirectErrorStream(true);

        try {
            Process process = builder.start();

            String output = new String(
                    process.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8
            );

            boolean completed = process.waitFor(
                    15,
                    TimeUnit.SECONDS
            );

            if (!completed) {
                process.destroyForcibly();
                throw new DeploymentException(
                        "Railway CLI version check timed out."
                );
            }

            if (process.exitValue() != 0) {
                throw new DeploymentException(
                        "Railway CLI version check failed."
                );
            }

            return output.trim();

        } catch (IOException e) {
            throw new DeploymentException(
                    "Railway CLI not available: " + e.getMessage()
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeploymentException(
                    "Railway CLI version check interrupted."
            );
        }
    }


    private void validateId(String value, String fieldName) {

        if (value == null || value.isBlank()) {
            throw new DeploymentException(
                    fieldName + " is required."
            );
        }
    }


    private ReentrantLock getDeploymentLock(
            String projectId,
            String serviceId,
            String environmentId
    ) {
        validateId(projectId, "Railway project ID");
        validateId(serviceId, "Railway service ID");
        validateId(environmentId, "Railway environment ID");

        String key = projectId + ":" + serviceId + ":" + environmentId;

        return deploymentLocks.computeIfAbsent(
                key,
                ignored -> new ReentrantLock()
        );
    }



}
