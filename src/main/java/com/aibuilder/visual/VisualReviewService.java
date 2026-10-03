package com.aibuilder.visual;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class VisualReviewService {

    private final ChatClient.Builder chatClientBuilder;

    public VisualReviewResult review(
            Path viewportScreenshot,
            Path fullPageScreenshot
    ) {

        try {

            VisualReviewResult result =
                    chatClientBuilder
                            .build()
                            .prompt()

                            .system("""
You are the visual QA reviewer for an AI application builder.

You are reviewing a website that has already compiled successfully
and has been rendered inside a real Chromium browser.

You will receive:

1. A 1440x1000 desktop viewport screenshot.
2. A full-page desktop screenshot.

Your job is NOT to redesign the website according to personal taste.

Identify only clear implementation or rendering defects.

Return NEEDS_FIX for significant problems such as:

- blank or nearly blank application
- extremely narrow or incorrectly constrained desktop layout
- severe overflow or clipping
- overlapping sections
- broken responsive layout
- giant or malformed icons
- obviously broken image sizing
- unreadable text/background contrast
- browser-default styling where a designed UI was intended
- major spacing/layout failures
- stylesheet or design system clearly not being applied
- sections rendered in obviously unintended positions
- content compressed into unusable dimensions

Do NOT request changes merely because:

- another color might look better
- another font might look better
- minor spacing could be improved
- you personally prefer another design

PASS means the application is visually coherent,
usable, intentionally styled and free from major rendering defects.

Base the verdict only on significant visible defects.
""")

                            .user(user -> user
                                    .text("""
Review these screenshots of the generated application.

Image 1:
1440x1000 desktop viewport.

Image 2:
complete full-page desktop render.

Evaluate significant visible implementation defects only.
""")

                                    .media(
                                            MimeTypeUtils.IMAGE_PNG,
                                            new FileSystemResource(
                                                    viewportScreenshot
                                            )
                                    )

                                    .media(
                                            MimeTypeUtils.IMAGE_PNG,
                                            new FileSystemResource(
                                                    fullPageScreenshot
                                            )
                                    )
                            )

                            .call()

                            .entity(
                                    VisualReviewResult.class
                            );

            if (result == null) {

                throw new RuntimeException(
                        "Visual reviewer returned no result"
                );
            }

            if (result.verdict() == null) {

                throw new RuntimeException(
                        "Visual reviewer returned no verdict"
                );
            }

            System.out.println(
                    "[VISUAL REVIEW] Verdict: "
                            + result.verdict()
            );

            System.out.println(
                    "[VISUAL REVIEW] Summary: "
                            + result.summary()
            );

            if (result.issues() != null) {

                result.issues().forEach(
                        issue ->
                                System.out.println(
                                        "[VISUAL REVIEW] Issue: "
                                                + issue
                                )
                );
            }

            System.out.println(
                    "[VISUAL REVIEW] Fix instructions: "
                            + result.fixInstructions()
            );

            return result;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Visual review failed: "
                            + e.getMessage(),
                    e
            );
        }
    }
}