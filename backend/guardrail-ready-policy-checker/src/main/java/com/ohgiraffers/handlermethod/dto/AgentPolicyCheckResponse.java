package com.ohgiraffers.handlermethod.dto;

public record AgentPolicyCheckResponse(
        String decision,
        String policyName,
        String reason,
        boolean allowed,
        boolean guardrailReady,
        String traceId
) {
}
