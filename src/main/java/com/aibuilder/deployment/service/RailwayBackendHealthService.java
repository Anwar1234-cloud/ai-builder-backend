
package com.aibuilder.deployment.service;

import com.aibuilder.deployment.exception.DeploymentException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class RailwayBackendHealthService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private void verifyHealthOnce(String backendUrl){

        if (backendUrl == null || backendUrl.isBlank()) {
            throw new DeploymentException(
                    "Railway backend URL is missing."
            );
        }

        URI uri;

        try {
            URI baseUri = URI.create(backendUrl.trim());

            String hostname = baseUri.getHost();

            if (hostname == null
                    || !hostname.toLowerCase(java.util.Locale.ROOT)
                    .endsWith(".up.railway.app")) {

                throw new IllegalArgumentException(
                        "Backend URL must use an approved Railway domain."
                );
            }

            if (!"https".equalsIgnoreCase(baseUri.getScheme())
                    || baseUri.getHost() == null
                    || baseUri.getUserInfo() != null
                    || baseUri.getQuery() != null
                    || baseUri.getFragment() != null
                    || (baseUri.getPath() != null
                    && !baseUri.getPath().isEmpty()
                    && !"/".equals(baseUri.getPath()))) {

                throw new IllegalArgumentException(
                        "Invalid backend HTTPS origin."
                );
            }

            uri = URI.create(
                    baseUri.toString().replaceAll("/+$", "")
                            + "/api/health"
            );

        } catch (IllegalArgumentException e) {
            throw new DeploymentException(
                    "Invalid Railway backend URL.",
                    e
            );
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .build();

        try {

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (response.statusCode() != 200) {
                throw new DeploymentException(
                        "Railway backend health check failed. HTTP "
                                + response.statusCode()
                );
            }

            String body = response.body();

            try {

                JsonNode health = objectMapper.readTree(body);

                if (!"UP".equals(health.path("status").asText())
                        || !"generated-backend".equals(
                        health.path("service").asText())) {

                    throw new DeploymentException(
                            "Railway backend returned an unexpected health response."
                    );
                }

            } catch (tools.jackson.core.JacksonException e) {

                throw new DeploymentException(
                        "Railway backend returned invalid health JSON.",
                        e
                );
            }

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new DeploymentException(
                    "Railway backend health check interrupted.",
                    e
            );

        } catch (java.io.IOException e) {

            throw new DeploymentException(
                    "Railway backend health check request failed.",
                    e
            );
        }
    }

    public void verifyHealth(String backendUrl) {

        int maxAttempts = 12;
        long retryDelayMillis = 5000;

        DeploymentException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            try {
                verifyHealthOnce(backendUrl);
                return;

            } catch (DeploymentException e) {

                lastFailure = e;

                if (attempt == maxAttempts) {
                    break;
                }

                try {
                    Thread.sleep(retryDelayMillis);

                } catch (InterruptedException interruptedException) {

                    Thread.currentThread().interrupt();

                    throw new DeploymentException(
                            "Railway backend health verification interrupted.",
                            interruptedException
                    );
                }
            }
        }

        throw new DeploymentException(
                "Railway backend health check failed after "
                        + maxAttempts + " attempts.",
                lastFailure
        );
    }
}
