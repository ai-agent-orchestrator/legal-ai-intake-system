package com.ohgiraffers.handlermethod.dto;

import java.util.Map;

public record LegalIntakeSummaryResponse(
        long total,
        long high,
        long medium,
        long low,
        Map<String, Long> decisionCounts,
        Map<String, Long> nextStepCounts
) {
}
