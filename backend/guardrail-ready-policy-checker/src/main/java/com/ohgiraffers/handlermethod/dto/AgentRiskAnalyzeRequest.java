package com.ohgiraffers.handlermethod.dto;

public record AgentRiskAnalyzeRequest(
        String userInput,
        String toolName,
        Integer retryCount,
        Integer promptTokens,
        Integer completionTokens,
        Boolean policyViolation,
        Boolean toolError,
        Boolean externalApiCall,
        Boolean approvalRequired,
        Boolean dbWrite
) {
}
