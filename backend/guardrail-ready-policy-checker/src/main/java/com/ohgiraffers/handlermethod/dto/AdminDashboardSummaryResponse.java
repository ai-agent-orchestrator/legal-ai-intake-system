package com.ohgiraffers.handlermethod.dto;

public record AdminDashboardSummaryResponse(
        long agentRunCount,
        long riskHistoryCount,
        long securityEventCount,
        long openIncidentCount,
        AgentRiskSummaryResponse riskSummary
) {
}
