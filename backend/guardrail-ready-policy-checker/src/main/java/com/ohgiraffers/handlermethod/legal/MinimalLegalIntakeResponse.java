package com.ohgiraffers.handlermethod.legal;

import java.util.List;

public record MinimalLegalIntakeResponse(
        String sessionId,
        String inquiryPurpose,
        String caseType,
        String caseTypeLabel,
        String partyRole,
        String disputeArea,
        List<String> checkedFacts,
        List<String> recommendedDocuments,
        String desiredNextStep,
        String clientGuideMessage,
        String privacyNotice,
        String securityNotice
) {
}
