package com.ohgiraffers.handlermethod.legal;

import java.util.List;

public record LegalIntakeTemplate(
        LegalIntakeCaseType caseType,
        LegalProcedureDomain domain,
        String procedureCode,
        String displayName,
        List<String> keywords,
        List<String> requiredDocuments,
        List<String> intakeQuestions,
        List<String> nextSteps
) {
}
