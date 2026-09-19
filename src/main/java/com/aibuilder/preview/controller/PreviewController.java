package com.aibuilder.preview.controller;

import com.aibuilder.preview.service.PreviewService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/projects/{projectId}/previews")
@RequiredArgsConstructor
public class PreviewController {

    private final PreviewService previewService;

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

        /*
         * Vite normally generates:
         *
         * /assets/index-xxxxx.js
         *
         * But our preview lives under:
         *
         * /api/projects/{projectId}/previews/{buildId}/
         *
         * Rewrite the asset URLs so the browser requests
         * them from our preview endpoint.
         */
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
}