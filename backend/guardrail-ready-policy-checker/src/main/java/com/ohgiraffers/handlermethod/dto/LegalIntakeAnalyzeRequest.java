package com.ohgiraffers.handlermethod.dto;

public record LegalIntakeAnalyzeRequest(
        String caseType,
        String summary,
        String claimPurpose,
        Boolean hasEvidence,
        Boolean hasDeadline,
        Boolean opponentKnown,
        Boolean damageAmountKnown,
        String currentStage,
        Boolean urgent
) {
}
