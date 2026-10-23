package com.ohgiraffers.handlermethod.dto;

import java.util.List;

public record LegalIntakeClassificationResponse(
        String caseType,
        String caseTypeLabel,
        String disputeArea,
        List<String> recommendedDocuments,
        String clientGuideMessage,
        String classificationMethod,
        boolean persisted
) {
}
