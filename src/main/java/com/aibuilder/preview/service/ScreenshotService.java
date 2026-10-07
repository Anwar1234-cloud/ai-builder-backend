package com.aibuilder.preview.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
public class ScreenshotService {

    private static final int DESKTOP_WIDTH = 1440;
    private static final int DESKTOP_HEIGHT = 1000;

    @Value("${app.preview.base-url:http://localhost:8080}")
    private String previewBaseUrl;

    public ScreenshotResult captureScreenshots(
            Long projectId,
            Long buildId
    ) {

        Path screenshotDirectory =
                Paths.get(
                                "runtime",
                                "screenshots",
                                "project-" + projectId
                        )
                        .toAbsolutePath()
                        .normalize();

        Path viewportPath =
                screenshotDirectory
                        .resolve(
                                "build-" + buildId
                                        + "-viewport.png"
                        )
                        .normalize();

        Path fullPagePath =
                screenshotDirectory
                        .resolve(
                                "build-" + buildId
                                        + "-fullpage.png"
                        )
                        .normalize();

        validateScreenshotPath(
                screenshotDirectory,
                viewportPath
        );

        validateScreenshotPath(
                screenshotDirectory,
                fullPagePath
        );

        try {

            Files.createDirectories(
                    screenshotDirectory
            );

            String previewUrl =
                    previewBaseUrl
                            + "/api/projects/"
                            + projectId
                            + "/previews/"
                            + buildId;

            System.out.println(
                    "[VISUAL] Opening preview: "
                            + previewUrl
            );

            try (
                    Playwright playwright =
                            Playwright.create();

                    Browser browser =
                            playwright
                                    .chromium()
                                    .launch(
                                            new BrowserType
                                                    .LaunchOptions()
                                                    .setHeadless(true)
                                    )
            ) {

                Page page =
                        browser.newPage(
                                new Browser.NewPageOptions()
                                        .setViewportSize(
                                                DESKTOP_WIDTH,
                                                DESKTOP_HEIGHT
                                        )
                        );

                registerBrowserLogging(page);

                page.navigate(
                        previewUrl,
                        new Page.NavigateOptions()
                                .setWaitUntil(
                                        WaitUntilState
                                                .DOMCONTENTLOADED
                                )
                                .setTimeout(
                                        30_000
                                )
                );

                page.waitForTimeout(1000);

                String bodyText =
                        page.locator("body")
                                .innerText();

                int bodyTextLength =
                        bodyText == null
                                ? 0
                                : bodyText.trim().length();

                System.out.println(
                        "[VISUAL] Final URL: "
                                + page.url()
                );

                System.out.println(
                        "[VISUAL] Page title: "
                                + page.title()
                );

                System.out.println(
                        "[VISUAL] Body text length: "
                                + bodyTextLength
                );

                /*
                 * Screenshot #1
                 *
                 * Exactly what a desktop user sees
                 * above the fold.
                 */
                page.screenshot(
                        new Page.ScreenshotOptions()
                                .setPath(viewportPath)
                                .setFullPage(false)
                );

                System.out.println(
                        "[VISUAL] Viewport screenshot: "
                                + viewportPath
                );

                /*
                 * Screenshot #2
                 *
                 * Entire generated page.
                 */
                page.screenshot(
                        new Page.ScreenshotOptions()
                                .setPath(fullPagePath)
                                .setFullPage(true)
                );

                System.out.println(
                        "[VISUAL] Full-page screenshot: "
                                + fullPagePath
                );
            }

            if (!Files.exists(viewportPath)) {

                throw new RuntimeException(
                        "Viewport screenshot was not created"
                );
            }

            if (!Files.exists(fullPagePath)) {

                throw new RuntimeException(
                        "Full-page screenshot was not created"
                );
            }

            return new ScreenshotResult(
                    viewportPath,
                    fullPagePath
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to capture preview screenshots: "
                            + e.getMessage(),
                    e
            );
        }
    }

    /*
     * Keep compatibility with the temporary
     * controller endpoint we already created.
     */
    public Path captureDesktopScreenshot(
            Long projectId,
            Long buildId
    ) {

        return captureScreenshots(
                projectId,
                buildId
        ).viewportScreenshot();
    }

    private void validateScreenshotPath(
            Path screenshotDirectory,
            Path screenshotPath
    ) {

        if (!screenshotPath.startsWith(
                screenshotDirectory
        )) {

            throw new SecurityException(
                    "Invalid screenshot path"
            );
        }
    }

    private void registerBrowserLogging(
            Page page
    ) {

        page.onConsoleMessage(message ->
                System.out.println(
                        "[BROWSER CONSOLE] "
                                + message.type()
                                + ": "
                                + message.text()
                )
        );

        page.onPageError(error ->
                System.out.println(
                        "[BROWSER PAGE ERROR] "
                                + error
                )
        );

        page.onRequestFailed(request ->
                System.out.println(
                        "[BROWSER REQUEST FAILED] "
                                + request.url()
                                + " -> "
                                + request.failure()
                )
        );

        page.onResponse(response -> {

            if (response.status() >= 400) {

                System.out.println(
                        "[BROWSER HTTP ERROR] "
                                + response.status()
                                + " "
                                + response.url()
                );
            }
        });
    }

    public record ScreenshotResult(
            Path viewportScreenshot,
            Path fullPageScreenshot
    ) {
    }
}