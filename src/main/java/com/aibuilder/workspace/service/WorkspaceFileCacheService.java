package com.aibuilder.workspace.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WorkspaceFileCacheService {


    private final Map<String, String> cache =
            new ConcurrentHashMap<>();


    private String key(
            Long agentRunId,
            String path
    ) {

        if (agentRunId == null) {
            throw new IllegalArgumentException(
                    "agentRunId cannot be null"
            );
        }

        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(
                    "path cannot be empty"
            );
        }

        return agentRunId + ":" + path;
    }



    public String get(
            Long agentRunId,
            String path
    ) {

        return cache.get(
                key(agentRunId, path)
        );
    }



    public void put(
            Long agentRunId,
            String path,
            String content
    ) {

        if (content == null) {
            content = "";
        }

        cache.put(
                key(agentRunId, path),
                content
        );
    }



    public void remove(
            Long agentRunId,
            String path
    ) {

        cache.remove(
                key(agentRunId, path)
        );
    }



    public void invalidate(
            Long agentRunId,
            String path
    ) {

        remove(
                agentRunId,
                path
        );
    }



    public void clear(
            Long agentRunId
    ) {

        if (agentRunId == null) {
            return;
        }

        String prefix =
                agentRunId + ":";

        cache.keySet().removeIf(
                cacheKey ->
                        cacheKey.startsWith(prefix)
        );
    }



    public boolean contains(
            Long agentRunId,
            String path
    ) {

        return cache.containsKey(
                key(agentRunId, path)
        );
    }
}