package com.aibuilder.preview.controller;

import com.aibuilder.preview.service.PreviewService;
import com.aibuilder.preview.service.ScreenshotService;
import com.aibuilder.visual.VisualReviewService;
import com.aibuilder.visual.VisualReviewResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import com.aibuilder.project.entity.ProjectType;
import com.aibuilder.project.repository.ProjectRepository;

import java.io.IOException;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/projects/{projectId}/previews")
@RequiredArgsConstructor
public class PreviewController {

    private final PreviewService previewService;
    private final ScreenshotService screenshotService;
    private final VisualReviewService visualReviewService;
    private final ProjectRepository projectRepository;

    @GetMapping("/{buildId}")
    public ResponseEntity<String> previewIndex(
            @PathVariable Long projectId,
            @PathVariable Long buildId
    ) throws IOException {

        Resource resource =
                previewService.getPreviewFile(
                        projectId,
                        buildId,
                        "index.html"
                );

        String html =
                StreamUtils.copyToString(
                        resource.getInputStream(),
                        StandardCharsets.UTF_8
                );


        String previewBase =
                "/api/projects/"
                        + projectId
                        + "/previews/"
                        + buildId
                        + "/";

        html = html.replace(
                "src=\"/assets/",
                "src=\"" + previewBase + "assets/"
        );

        html = html.replace(
                "href=\"/assets/",
                "href=\"" + previewBase + "assets/"
        );

        html = html.replace(
                "src='/assets/",
                "src='" + previewBase + "assets/"
        );

        html = html.replace(
                "href='/assets/",
                "href='" + previewBase + "assets/"
        );

        var project = projectRepository.findById(projectId)
                .orElseThrow(() ->
                        new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.NOT_FOUND,
                                "Project not found"
                        )
                );

        if (project.getType() == ProjectType.FULL_STACK_APP) {
            String bridge = fullStackApiBridge(projectId);

            if (html.contains("</head>")) {
                html = html.replace("</head>", bridge + "\n</head>");
            } else {
                html = bridge + html;
            }
        }

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                "text/html;charset=UTF-8"
                        )
                )
                .body(html);
    }

    @GetMapping("/{buildId}/**")
    public ResponseEntity<Resource> previewFile(
            @PathVariable Long projectId,
            @PathVariable Long buildId,
            HttpServletRequest request
    ) {

        String requestUri =
                request.getRequestURI();

        String prefix =
                "/api/projects/"
                        + projectId
                        + "/previews/"
                        + buildId
                        + "/";

        String path =
                requestUri.startsWith(prefix)
                        ? requestUri.substring(
                        prefix.length()
                )
                        : "index.html";

        Resource resource =
                previewService.getPreviewFile(
                        projectId,
                        buildId,
                        path
                );

        String contentType =
                URLConnection.guessContentTypeFromName(
                        resource.getFilename()
                );

        MediaType mediaType =
                contentType != null
                        ? MediaType.parseMediaType(
                        contentType
                )
                        : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(resource);
    }

    @PostMapping("/{buildId}/screenshot")
    public ResponseEntity<String> captureScreenshot(
            @PathVariable Long projectId,
            @PathVariable Long buildId
    ) {

        var screenshot =
                screenshotService
                        .captureDesktopScreenshot(
                                projectId,
                                buildId
                        );

        return ResponseEntity.ok(
                screenshot.toString()
        );
    }
    @PostMapping("/{buildId}/visual-review")
    public ResponseEntity<VisualReviewResult> reviewVisuals(
            @PathVariable Long projectId,
            @PathVariable Long buildId
    ) {

        ScreenshotService.ScreenshotResult screenshots =
                screenshotService.captureScreenshots(
                        projectId,
                        buildId
                );

        VisualReviewResult result =
                visualReviewService.review(
                        screenshots.viewportScreenshot(),
                        screenshots.fullPageScreenshot()
                );

        return ResponseEntity.ok(result);
    }

    private String fullStackApiBridge(Long projectId) {
        String apiPrefix =
                "/api/projects/" + projectId + "/runtime/api";

        return """
        <script>
        (() => {
          const API_PREFIX = '%s';
          const nativeFetch = window.fetch.bind(window);

          function rewriteUrl(input) {
            const url = new URL(input, window.location.href);

            if (
              url.origin === window.location.origin &&
              (url.pathname === '/api' ||
               url.pathname.startsWith('/api/')) &&
              !url.pathname.startsWith('/api/projects/')
            ) {
              return API_PREFIX +
                url.pathname.substring(4) +
                url.search +
                url.hash;
            }

            return input;
          }

          window.fetch = (input, init) => {
            if (input instanceof Request) {
              const rewritten = rewriteUrl(input.url);
              if (rewritten !== input.url) {
                return nativeFetch(
                  new Request(rewritten, input),
                  init
                );
              }
              return nativeFetch(input, init);
            }

            return nativeFetch(rewriteUrl(input), init);
          };

          const nativeOpen = XMLHttpRequest.prototype.open;

          XMLHttpRequest.prototype.open = function(
            method, url, ...args
          ) {
            return nativeOpen.call(
              this,
              method,
              rewriteUrl(url),
              ...args
            );
          };
        })();
        </script>
        """.formatted(apiPrefix);
    }
}