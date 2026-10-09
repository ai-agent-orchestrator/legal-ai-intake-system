package com.ohgiraffers.handlermethod.dto;

import java.util.Map;

public record AgentRiskSummaryResponse(
        long total,
        long high,
        long medium,
        long low,
        Map<String, Long> decisionCounts,
        Map<String, Long> recommendedActionCounts
) {
}
