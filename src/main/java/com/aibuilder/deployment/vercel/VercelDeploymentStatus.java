package com.aibuilder.deployment.vercel;

public record VercelDeploymentStatus(
        String state,
        String readyState,
        String url,
        String errorMessage
) {

    public boolean isReady() {
        return "READY".equalsIgnoreCase(readyState)
                || "READY".equalsIgnoreCase(state);
    }

    public boolean isFailed() {
        return "ERROR".equalsIgnoreCase(readyState)
                || "ERROR".equalsIgnoreCase(state)
                || "CANCELED".equalsIgnoreCase(readyState)
                || "CANCELED".equalsIgnoreCase(state);
    }
}