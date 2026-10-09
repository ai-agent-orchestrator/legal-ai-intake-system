package com.ohgiraffers.handlermethod.guardrail;

public record AgentPolicyDecision(
        AgentPolicyDecisionType decision,
        String policyName,
        String reason,
        boolean guardrailReady
) {
    public boolean allowed() {
        return decision == AgentPolicyDecisionType.ALLOWED;
    }
}
