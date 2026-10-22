
package com.aibuilder.deployment.railway;

import com.aibuilder.deployment.exception.DeploymentException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Service
public class RailwayCliDomainService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String getExistingDomain(
            String projectId,
            String environmentId,
            String serviceId
    ) {
        if (projectId == null || projectId.isBlank()
                || environmentId == null || environmentId.isBlank()
                || serviceId == null || serviceId.isBlank()) {
            throw new DeploymentException(
                    "Railway project, environment and service IDs are required."
            );
        }

        ProcessBuilder builder = new ProcessBuilder(
                "railway.cmd",
                "domain",
                "list",
                "--project", projectId,
                "--service", serviceId,
                "--environment", environmentId,
                "--json"
        );

        builder.redirectErrorStream(true);

        try {
            Process process = builder.start();

            boolean completed = process.waitFor(
                    30,
                    TimeUnit.SECONDS
            );

            if (!completed) {
                process.destroyForcibly();
                throw new DeploymentException(
                        "Railway domain lookup timed out."
                );
            }

            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            if (process.exitValue() != 0) {
                throw new DeploymentException(
                        "Railway domain lookup failed: " + output
                );
            }

            JsonNode root = objectMapper.readTree(output);
            JsonNode domains = root.path("domains");

            if (!domains.isArray()) {
                throw new DeploymentException(
                        "Invalid Railway domain response."
                );
            }

            for (JsonNode item : domains) {
                String domain = item.path("domain").asText();
                String syncStatus = item.path("syncStatus").asText();

                if (!domain.isBlank()
                        && "ACTIVE".equalsIgnoreCase(syncStatus)) {
                    return "https://" + domain;
                }
            }

            throw new DeploymentException(
                    "No active Railway domain found."
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeploymentException(
                    "Railway domain lookup interrupted.",
                    e
            );
        } catch (IOException e) {
            throw new DeploymentException(
                    "Failed to execute Railway CLI domain lookup.",
                    e
            );
        }
    }
}
