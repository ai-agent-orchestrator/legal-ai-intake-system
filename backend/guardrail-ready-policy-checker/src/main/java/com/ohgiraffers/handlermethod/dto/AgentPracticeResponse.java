package com.ohgiraffers.handlermethod.dto;

public record AgentPracticeResponse(
        String mode,
        String decision,
        String message,
        String toolName,
        int planSteps,
        int retryCount,
        int totalTokens,
        String traceId
) {
}
