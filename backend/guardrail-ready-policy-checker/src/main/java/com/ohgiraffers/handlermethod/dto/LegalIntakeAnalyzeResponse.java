package com.ohgiraffers.handlermethod.dto;

import java.util.List;

public record LegalIntakeAnalyzeResponse(
        String decision,
        int readinessScore,
        String readinessLevel,
        List<String> signals,
        String recommendedNextStep,
        boolean ontologyDraft,
        Long historyId,
        String traceId
) {
}
