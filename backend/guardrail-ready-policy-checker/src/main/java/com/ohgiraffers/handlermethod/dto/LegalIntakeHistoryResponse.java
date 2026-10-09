package com.ohgiraffers.handlermethod.dto;

import com.ohgiraffers.handlermethod.entity.LegalIntakeHistory;

import java.time.LocalDateTime;
import java.util.List;

public record LegalIntakeHistoryResponse(
        Long id,
        String caseType,
        String summary,
        String claimPurpose,
        String currentStage,
        String decision,
        int readinessScore,
        String readinessLevel,
        List<String> signals,
        String recommendedNextStep,
        String traceId,
        LocalDateTime createdAt
) {

    public static LegalIntakeHistoryResponse from(LegalIntakeHistory history) {
        return new LegalIntakeHistoryResponse(
                history.getId(),
                history.getCaseType(),
                history.getSummary(),
                history.getClaimPurpose(),
                history.getCurrentStage(),
                history.getDecision(),
                history.getReadinessScore(),
                history.getReadinessLevel(),
                splitSignals(history.getSignals()),
                history.getRecommendedNextStep(),
                history.getTraceId(),
                history.getCreatedAt()
        );
    }

    private static List<String> splitSignals(String signals) {
        if (signals == null || signals.isBlank()) {
            return List.of();
        }

        return List.of(signals.split(","));
    }
}
