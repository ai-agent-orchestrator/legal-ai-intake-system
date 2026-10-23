package com.ohgiraffers.handlermethod.legal;

import java.util.List;

public record MinimalLegalIntakeRequest(
        String sessionId,
        String inquiryPurpose,
        String caseType,
        String partyRole,
        String incidentDate,
        String counterpart,
        String amountOrDamage,
        List<String> documents,
        String desiredNextStep,
        String coreFact
) {
}
