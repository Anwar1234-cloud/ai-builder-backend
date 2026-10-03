package com.aibuilder.build.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProjectIntegrityService {

    private static final Pattern IMPORT_PATTERN =
            Pattern.compile(
                    "(?:import\\s+(?:[^;]*?\\s+from\\s+)?|import\\s*\\()"
                            + "[\"'](\\.{1,2}/[^\"']+)[\"']"
            );

    public ValidationResult validate(Path workspace) {

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (workspace == null || !Files.exists(workspace)) {
            errors.add("Project workspace does not exist.");
            return new ValidationResult(false, errors, warnings);
        }

        Path packageJson = workspace.resolve("package.json");
        Path indexHtml = workspace.resolve("index.html");
        Path srcDirectory = workspace.resolve("src");
        Path mainJsx = srcDirectory.resolve("main.jsx");
        Path mainJs = srcDirectory.resolve("main.js");

        if (!Files.exists(packageJson)) {
            errors.add("Missing required file: package.json");
        }

        if (!Files.exists(indexHtml)) {
            errors.add("Missing required file: index.html");
        }

        if (!Files.exists(srcDirectory)) {
            errors.add("Missing required directory: src");
        }

        Path entryFile = null;

        if (Files.exists(mainJsx)) {
            entryFile = mainJsx;
        } else if (Files.exists(mainJs)) {
            entryFile = mainJs;
        } else {
            errors.add(
                    "Missing React/Vite entry file: "
                            + "src/main.jsx or src/main.js"
            );
        }

        try {

            if (Files.exists(srcDirectory)) {
                validateLocalImports(
                        workspace,
                        srcDirectory,
                        errors
                );
            }

            if (entryFile != null) {
                validateStylesheetIntegration(
                        workspace,
                        srcDirectory,
                        entryFile,
                        errors,
                        warnings
                );
            }

        } catch (IOException e) {

            errors.add(
                    "Failed to inspect project integrity: "
                            + e.getMessage()
            );
        }

        return new ValidationResult(
                errors.isEmpty(),
                errors,
                warnings
        );
    }


    private void validateLocalImports(
            Path workspace,
            Path srcDirectory,
            List<String> errors
    ) throws IOException {

        try (var paths = Files.walk(srcDirectory)) {

            List<Path> sourceFiles =
                    paths.filter(Files::isRegularFile)
                            .filter(this::isJavaScriptSource)
                            .toList();

            for (Path sourceFile : sourceFiles) {

                String content =
                        Files.readString(
                                sourceFile,
                                StandardCharsets.UTF_8
                        );

                Matcher matcher =
                        IMPORT_PATTERN.matcher(content);

                while (matcher.find()) {

                    String importPath =
                            matcher.group(1);

                    if (!localImportExists(
                            sourceFile,
                            importPath
                    )) {

                        errors.add(
                                "Unresolved local import '"
                                        + importPath
                                        + "' in "
                                        + relative(
                                        workspace,
                                        sourceFile
                                )
                        );
                    }
                }
            }
        }
    }


    private boolean localImportExists(
            Path sourceFile,
            String importPath
    ) {

        Path parent =
                sourceFile.getParent();

        if (parent == null) {
            return false;
        }

        Path base =
                parent.resolve(importPath)
                        .normalize();

        if (Files.isRegularFile(base)) {
            return true;
        }

        String[] extensions = {
                ".js",
                ".jsx",
                ".ts",
                ".tsx",
                ".css",
                ".json"
        };

        for (String extension : extensions) {

            if (Files.isRegularFile(
                    Path.of(
                            base.toString()
                                    + extension
                    )
            )) {
                return true;
            }
        }

        if (Files.isDirectory(base)) {

            for (String extension : extensions) {

                if (Files.isRegularFile(
                        base.resolve(
                                "index" + extension
                        )
                )) {
                    return true;
                }
            }
        }

        return false;
    }


    private void validateStylesheetIntegration(
            Path workspace,
            Path srcDirectory,
            Path entryFile,
            List<String> errors,
            List<String> warnings
    ) throws IOException {

        List<Path> cssFiles;

        try (var paths = Files.walk(srcDirectory)) {

            cssFiles =
                    paths.filter(Files::isRegularFile)
                            .filter(path ->
                                    path.getFileName()
                                            .toString()
                                            .endsWith(".css")
                            )
                            .toList();
        }

        if (cssFiles.isEmpty()) {

            warnings.add(
                    "No CSS files were found under src."
            );

            return;
        }

        boolean anyCssReachable =
                isAnyCssReachableFromEntry(
                        entryFile,
                        cssFiles,
                        new ArrayList<>()
                );

        if (!anyCssReachable) {

            errors.add(
                    "CSS_INTEGRATION_ERROR: CSS files exist under "
                            + "src but no stylesheet is reachable from "
                            + relative(workspace, entryFile)
                            + ". Import the application's stylesheet "
                            + "from the React entry/component tree."
            );
        }
    }


    private boolean isAnyCssReachableFromEntry(
            Path sourceFile,
            List<Path> cssFiles,
            List<Path> visited
    ) throws IOException {

        Path normalizedSource =
                sourceFile.toAbsolutePath()
                        .normalize();

        if (visited.contains(normalizedSource)) {
            return false;
        }

        visited.add(normalizedSource);

        if (!Files.exists(normalizedSource)
                || !Files.isRegularFile(normalizedSource)) {
            return false;
        }

        String content =
                Files.readString(
                        normalizedSource,
                        StandardCharsets.UTF_8
                );

        Matcher matcher =
                IMPORT_PATTERN.matcher(content);

        while (matcher.find()) {

            String importPath =
                    matcher.group(1);

            Path resolved =
                    resolveLocalImport(
                            normalizedSource,
                            importPath
                    );

            if (resolved == null) {
                continue;
            }

            if (resolved.getFileName()
                    .toString()
                    .endsWith(".css")) {

                Path normalizedResolved =
                        resolved.toAbsolutePath()
                                .normalize();

                boolean knownCss =
                        cssFiles.stream()
                                .map(path ->
                                        path.toAbsolutePath()
                                                .normalize()
                                )
                                .anyMatch(
                                        normalizedResolved::equals
                                );

                if (knownCss) {
                    return true;
                }
            }

            if (isJavaScriptSource(resolved)) {

                if (isAnyCssReachableFromEntry(
                        resolved,
                        cssFiles,
                        visited
                )) {
                    return true;
                }
            }
        }

        return false;
    }


    private Path resolveLocalImport(
            Path sourceFile,
            String importPath
    ) {

        Path parent =
                sourceFile.getParent();

        if (parent == null) {
            return null;
        }

        Path base =
                parent.resolve(importPath)
                        .normalize();

        if (Files.isRegularFile(base)) {
            return base;
        }

        String[] extensions = {
                ".js",
                ".jsx",
                ".ts",
                ".tsx",
                ".css",
                ".json"
        };

        for (String extension : extensions) {

            Path candidate =
                    Path.of(
                            base.toString()
                                    + extension
                    );

            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        if (Files.isDirectory(base)) {

            for (String extension : extensions) {

                Path candidate =
                        base.resolve(
                                "index" + extension
                        );

                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }

        return null;
    }


    private boolean isJavaScriptSource(Path path) {

        String name =
                path.getFileName()
                        .toString()
                        .toLowerCase();

        return name.endsWith(".js")
                || name.endsWith(".jsx")
                || name.endsWith(".ts")
                || name.endsWith(".tsx");
    }


    private String relative(
            Path workspace,
            Path file
    ) {

        try {

            return workspace
                    .toAbsolutePath()
                    .normalize()
                    .relativize(
                            file.toAbsolutePath()
                                    .normalize()
                    )
                    .toString()
                    .replace("\\", "/");

        } catch (Exception e) {

            return file.toString();
        }
    }


    public record ValidationResult(
            boolean valid,
            List<String> errors,
            List<String> warnings
    ) {

        public String formattedErrors() {

            if (errors == null || errors.isEmpty()) {
                return "";
            }

            return String.join(
                    System.lineSeparator(),
                    errors
            );
        }

        public String formattedWarnings() {

            if (warnings == null || warnings.isEmpty()) {
                return "";
            }

            return String.join(
                    System.lineSeparator(),
                    warnings
            );
        }
    }
}