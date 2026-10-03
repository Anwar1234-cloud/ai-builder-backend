package com.aibuilder.ai;

import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class AiGenerationRetryService {

    private static final int MAX_ATTEMPTS = 3;

    public String execute(
            String operationName,
            Supplier<String> aiCall
    ) {

        Exception lastException = null;

        for (int attempt = 1;
             attempt <= MAX_ATTEMPTS;
             attempt++) {

            try {

                System.out.println(
                        "[AI] "
                                + operationName
                                + " attempt "
                                + attempt
                                + "/"
                                + MAX_ATTEMPTS
                );

                String response =
                        aiCall.get();

                /*
                 * IMPORTANT:
                 *
                 * Tool-enabled agents may successfully execute
                 * tool calls without returning final text.
                 *
                 * Therefore an empty textual response is NOT
                 * automatically considered a generation failure.
                 */
                if (response == null ||
                        response.isBlank()) {

                    System.out.println(
                            "[AI] "
                                    + operationName
                                    + " completed with empty textual content. "
                                    + "Tool execution may have completed successfully."
                    );

                    return "";
                }

                System.out.println(
                        "[AI] "
                                + operationName
                                + " succeeded on attempt "
                                + attempt
                );

                return response;

            } catch (Exception e) {

                lastException = e;

                System.err.println(
                        "[AI] "
                                + operationName
                                + " failed on attempt "
                                + attempt
                                + "/"
                                + MAX_ATTEMPTS
                                + ": "
                                + getFullExceptionMessage(e)
                );

                if (!isRetryable(e)) {

                    System.err.println(
                            "[AI] Non-retryable error. "
                                    + "Stopping retries."
                    );

                    throw new RuntimeException(
                            operationName
                                    + " failed with a non-retryable error: "
                                    + e.getMessage(),
                            e
                    );
                }

                if (attempt == MAX_ATTEMPTS) {
                    break;
                }

                long delay =
                        switch (attempt) {
                            case 1 -> 2000L;
                            case 2 -> 5000L;
                            default -> 10000L;
                        };

                System.out.println(
                        "[AI] Retrying "
                                + operationName
                                + " in "
                                + delay
                                + " ms..."
                );

                try {

                    Thread.sleep(delay);

                } catch (InterruptedException interruptedException) {

                    Thread.currentThread()
                            .interrupt();

                    throw new RuntimeException(
                            "AI retry interrupted",
                            interruptedException
                    );
                }
            }
        }

        throw new RuntimeException(
                operationName
                        + " failed after "
                        + MAX_ATTEMPTS
                        + " attempts",
                lastException
        );
    }


    private boolean isRetryable(Exception e) {

        String message =
                getFullExceptionMessage(e)
                        .toLowerCase();

        return message.contains(
                "failed to generate content"
        )
                || message.contains("timeout")
                || message.contains("timed out")
                || message.contains("429")
                || message.contains("rate limit")
                || message.contains("resource_exhausted")
                || message.contains("503")
                || message.contains("service unavailable")
                || message.contains("connection")
                || message.contains("temporarily unavailable");
    }


    private String getFullExceptionMessage(
            Throwable throwable
    ) {

        StringBuilder message =
                new StringBuilder();

        Throwable current =
                throwable;

        while (current != null) {

            if (current.getMessage() != null) {

                if (!message.isEmpty()) {
                    message.append(" -> ");
                }

                message.append(
                        current.getMessage()
                );
            }

            current =
                    current.getCause();
        }

        return message.toString();
    }
}