package com.ohgiraffers.handlermethod.dto;

public record AgentPracticeRequest(
        String userInput,
        String toolName,
        Integer planSteps,
        Integer retryCount,
        Integer promptTokens,
        Integer completionTokens
) {
}
