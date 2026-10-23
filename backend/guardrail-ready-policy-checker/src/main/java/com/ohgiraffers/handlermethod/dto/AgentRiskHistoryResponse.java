package com.ohgiraffers.handlermethod.dto;

import com.ohgiraffers.handlermethod.entity.AgentRiskHistory;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public record AgentRiskHistoryResponse(
        Long id,
        String toolName,
        String decision,
        int riskScore,
        String riskLevel,
        List<String> signals,
        String recommendedAction,
        boolean guardrailReady,
        String traceId,
        LocalDateTime createdAt,
        com.ohgiraffers.handlermethod.risk.AgentRiskScoring.Breakdown scoreBreakdown
) {

    public static AgentRiskHistoryResponse from(AgentRiskHistory history) {
        return from(history, null);
    }

    public static AgentRiskHistoryResponse from(AgentRiskHistory history, Integer retryCount) {
        return new AgentRiskHistoryResponse(
                history.getId(),
                history.getToolName(),
                history.getDecision(),
                history.getRiskScore(),
                history.getRiskLevel(),
                splitSignals(history.getSignals()),
                history.getRecommendedAction(),
                history.isGuardrailReady(),
                history.getTraceId(),
                history.getCreatedAt(),
                com.ohgiraffers.handlermethod.risk.AgentRiskScoring.breakdown(
                        splitSignals(history.getSignals()), retryCount, history.getRiskScore())
        );
    }

    private static List<String> splitSignals(String signals) {
        if (signals == null || signals.isBlank()) {
            return List.of();
        }

        return Arrays.stream(signals.split(","))
                .map(String::trim)
                .filter(signal -> !signal.isBlank())
                .toList();
    }
}
