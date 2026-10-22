package com.aibuilder.deployment.vercel;

import com.aibuilder.deployment.exception.DeploymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

@Component
@RequiredArgsConstructor
public class VercelPrebuiltPackager {

    private final ObjectMapper objectMapper;


    public Path packageBuild(Path distDirectory) {

        if (distDirectory == null) {
            throw new DeploymentException(
                    "Build dist directory path cannot be null."
            );
        }

        Path resolvedDist = distDirectory;

        // Support full-stack projects: workspace/frontend/dist
        if (!Files.isDirectory(resolvedDist)) {
            Path workspace = distDirectory.getParent();

            if (workspace != null) {
                Path frontendDist = workspace
                        .resolve("frontend")
                        .resolve("dist");

                if (Files.isDirectory(frontendDist)) {
                    resolvedDist = frontendDist;
                }
            }
        }

        validateDist(resolvedDist);

        // Keep the original build workspace as the Vercel output root.
        Path workspace = distDirectory.getParent();

        Path vercelDirectory =
                workspace.resolve(".vercel");

        Path outputDirectory =
                vercelDirectory.resolve("output");

        Path staticDirectory =
                outputDirectory.resolve("static");

        try {
            deleteDirectory(vercelDirectory);

            Files.createDirectories(staticDirectory);

            copyDirectory(
                    resolvedDist,
                    staticDirectory
            );

            var root = objectMapper.createObjectNode();
            root.put("version", 3);

            var routes = root.putArray("routes");

            routes.addObject()
                    .put("handle", "filesystem");

            routes.addObject()
                    .put("src", "/(.*)")
                    .put("dest", "/index.html");

            Path configFile =
                    outputDirectory.resolve("config.json");

            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(
                            configFile.toFile(),
                            root
                    );

            return workspace;

        } catch (Exception e) {
            throw new DeploymentException(
                    "Failed to prepare Vercel prebuilt output: "
                            + (e.getMessage() != null
                            ? e.getMessage()
                            : e.getClass().getName()),
                    e
            );
        }
    }



    private void validateDist(
            Path distDirectory
    ) {

        if (distDirectory == null
                || !Files.isDirectory(distDirectory)) {

            throw new DeploymentException(
                    "Build dist directory does not exist: "
                            + distDirectory
            );
        }

        if (!Files.exists(
                distDirectory.resolve("index.html")
        )) {

            throw new DeploymentException(
                    "Deployable build does not contain "
                            + "dist/index.html."
            );
        }
    }


    private void copyDirectory(
            Path source,
            Path destination
    ) throws IOException {

        try (var stream = Files.walk(source)) {

            for (Path sourcePath :
                    stream.toList()) {

                Path relative =
                        source.relativize(
                                sourcePath
                        );

                Path destinationPath =
                        destination.resolve(
                                relative
                        );

                if (Files.isDirectory(sourcePath)) {

                    Files.createDirectories(
                            destinationPath
                    );

                } else {

                    Path parent =
                            destinationPath.getParent();

                    if (parent != null) {

                        Files.createDirectories(
                                parent
                        );
                    }

                    Files.copy(
                            sourcePath,
                            destinationPath,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }
            }
        }
    }


    private void deleteDirectory(
            Path directory
    ) throws IOException {

        if (!Files.exists(directory)) {
            return;
        }

        try (var stream = Files.walk(directory)) {

            for (Path path :
                    stream
                            .sorted(
                                    Comparator.reverseOrder()
                            )
                            .toList()) {

                Files.deleteIfExists(path);
            }
        }
    }
}