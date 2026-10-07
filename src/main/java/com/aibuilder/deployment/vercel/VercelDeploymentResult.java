package com.aibuilder.deployment.vercel;

public record VercelDeploymentResult(
        String deploymentId,
        String deploymentUrl
) {
}