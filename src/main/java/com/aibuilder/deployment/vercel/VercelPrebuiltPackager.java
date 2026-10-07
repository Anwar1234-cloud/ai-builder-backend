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

        validateDist(distDirectory);

        Path workspace =
                distDirectory.getParent();

        Path vercelDirectory =
                workspace.resolve(".vercel");

        Path outputDirectory =
                vercelDirectory.resolve("output");

        Path staticDirectory =
                outputDirectory.resolve("static");

        try {

            /*
             * Remove previous generated Vercel output so stale
             * files cannot leak into a new deployment.
             */
            deleteDirectory(vercelDirectory);

            Files.createDirectories(staticDirectory);

            /*
             * Copy everything from dist/ into
             * .vercel/output/static/.
             */
            copyDirectory(
                    distDirectory,
                    staticDirectory
            );

            /*
             * Build Output API configuration.
             *
             * version: 3 is required by Vercel.
             *
             * The routes below provide SPA fallback:
             * existing static assets are checked first,
             * then unknown routes fall back to index.html.
             */
            var root =
                    objectMapper.createObjectNode();

            root.put("version", 3);

            var routes =
                    root.putArray("routes");

            var filesystem =
                    routes.addObject();

            filesystem.put(
                    "handle",
                    "filesystem"
            );

            var fallback =
                    routes.addObject();

            fallback.put(
                    "src",
                    "/(.*)"
            );

            fallback.put(
                    "dest",
                    "/index.html"
            );

            Path configFile =
                    outputDirectory.resolve(
                            "config.json"
                    );

            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(
                            configFile.toFile(),
                            root
                    );

            return workspace;

        } catch (Exception e) {

            throw new DeploymentException(
                    "Failed to prepare Vercel prebuilt output: "
                            + (
                            e.getMessage() != null
                                    ? e.getMessage()
                                    : e.getClass().getName()
                    ),
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