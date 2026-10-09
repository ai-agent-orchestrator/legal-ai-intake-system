package com.ohgiraffers.handlermethod.risk;

public enum AgentRiskDecision {
    SAFE,
    UNSTABLE_TOOL_LOOP,
    SUSPICIOUS_RETRY,
    RISKY_EXTERNAL_ACCESS,
    APPROVAL_BYPASS_RISK,
    HIGH_RISK_AGENT_BEHAVIOR,
    SUSPICIOUS
}
