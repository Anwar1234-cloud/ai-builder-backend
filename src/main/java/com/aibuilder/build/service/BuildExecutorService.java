package com.aibuilder.build.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class BuildExecutorService {

    private static final int TIMEOUT_MINUTES = 5;

    private static final List<String> ALLOWED_COMMANDS = List.of(
            "npm run build",
            "npm install",
            "npm test",
            "mvn test",
            "mvn package"
    );

    public ExecutionResult execute(
            Path workspace,
            String command
    ) {

        validateCommand(command);

        try {

            ProcessBuilder processBuilder =
                    createProcessBuilder(
                            workspace,
                            command
                    );

            processBuilder.redirectErrorStream(false);

            Process process =
                    processBuilder.start();

            StringBuilder stdout =
                    new StringBuilder();

            StringBuilder stderr =
                    new StringBuilder();

            Thread stdoutThread =
                    new Thread(() ->
                            readStream(
                                    process.getInputStream(),
                                    stdout
                            )
                    );

            Thread stderrThread =
                    new Thread(() ->
                            readStream(
                                    process.getErrorStream(),
                                    stderr
                            )
                    );

            stdoutThread.start();
            stderrThread.start();

            boolean finished =
                    process.waitFor(
                            TIMEOUT_MINUTES,
                            TimeUnit.MINUTES
                    );

            if (!finished) {

                process.destroyForcibly();

                return new ExecutionResult(
                        -1,
                        stdout.toString(),
                        "Build timed out after "
                                + TIMEOUT_MINUTES
                                + " minutes"
                );
            }

            stdoutThread.join(5000);
            stderrThread.join(5000);

            return new ExecutionResult(
                    process.exitValue(),
                    stdout.toString(),
                    stderr.toString()
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to execute build command",
                    e
            );
        }
    }

    private ProcessBuilder createProcessBuilder(
            Path workspace,
            String command
    ) {

        if (command.equals("npm run build")) {
            return new ProcessBuilder(
                    "cmd.exe",
                    "/c",
                    "npm",
                    "run",
                    "build"
            ).directory(
                    workspace.toFile()
            );
        }

        if (command.equals("npm install")) {
            return new ProcessBuilder(
                    "cmd.exe",
                    "/c",
                    "npm",
                    "install"
            ).directory(
                    workspace.toFile()
            );
        }

        if (command.equals("npm test")) {
            return new ProcessBuilder(
                    "cmd.exe",
                    "/c",
                    "npm",
                    "test"
            ).directory(
                    workspace.toFile()
            );
        }

        if (command.equals("mvn test")) {
            return new ProcessBuilder(
                    "cmd.exe",
                    "/c",
                    "mvn",
                    "test"
            ).directory(
                    workspace.toFile()
            );
        }

        if (command.equals("mvn package")) {
            return new ProcessBuilder(
                    "cmd.exe",
                    "/c",
                    "mvn",
                    "package"
            ).directory(
                    workspace.toFile()
            );
        }

        throw new IllegalArgumentException(
                "Unsupported build command: " + command
        );
    }

    private void validateCommand(String command) {

        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException(
                    "Build command is required"
            );
        }

        if (!ALLOWED_COMMANDS.contains(command)) {
            throw new IllegalArgumentException(
                    "Unsupported build command: " + command
            );
        }
    }

    private void readStream(
            java.io.InputStream inputStream,
            StringBuilder output
    ) {

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        inputStream
                                )
                        )
        ) {

            String line;

            while ((line = reader.readLine()) != null) {
                output
                        .append(line)
                        .append(System.lineSeparator());
            }

        } catch (Exception e) {

            output
                    .append("Failed to read process output: ")
                    .append(e.getMessage());
        }
    }

    public record ExecutionResult(
            int exitCode,
            String output,
            String errorOutput
    ) {
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }
}