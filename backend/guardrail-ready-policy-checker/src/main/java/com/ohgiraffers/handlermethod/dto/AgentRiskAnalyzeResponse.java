package com.ohgiraffers.handlermethod.dto;

import java.util.List;

public record AgentRiskAnalyzeResponse(
        String decision,
        int riskScore,
        String riskLevel,
        List<String> signals,
        String recommendedAction,
        boolean guardrailReady,
        Long historyId,
        String traceId,
        com.ohgiraffers.handlermethod.risk.AgentRiskScoring.Breakdown scoreBreakdown
) {
}
