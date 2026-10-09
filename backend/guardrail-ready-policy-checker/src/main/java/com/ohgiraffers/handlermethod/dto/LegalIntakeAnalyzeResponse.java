package com.ohgiraffers.handlermethod.dto;

import java.util.List;

public record LegalIntakeAnalyzeResponse(
        String decision,
        int readinessScore,
        String readinessLevel,
        List<String> signals,
        String recommendedNextStep,
        boolean ontologyDraft,
        String matchedCaseType,
        String matchedDomain,
        String matchedProcedureCode,
        String matchedProcedureName,
        List<String> requiredDocuments,
        List<String> intakeQuestions,
        List<String> procedureNextSteps,
        Long historyId,
        String traceId
) {
}
