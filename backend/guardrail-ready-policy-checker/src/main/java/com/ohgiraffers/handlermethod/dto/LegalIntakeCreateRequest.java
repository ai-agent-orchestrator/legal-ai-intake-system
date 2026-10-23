package com.ohgiraffers.handlermethod.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record LegalIntakeCreateRequest(
        @Size(max = 100)
        String sessionId,

        @Size(max = 100)
        String clientName,

        @Size(max = 50)
        String clientPhone,

        @NotBlank
        @Size(max = 100)
        String inquiryPurpose,

        @NotBlank
        @Size(max = 50)
        String caseType,

        @NotBlank
        @Size(max = 50)
        String partyPosition,

        @Size(max = 30)
        String incidentDate,

        @Size(max = 100)
        String counterpart,

        @Size(max = 100)
        String amountOrDamage,

        @Size(max = 100)
        String currentStage,

        @NotBlank
        @Size(max = LegalInputLimits.MAX_CASE_STORY_CHARS)
        String coreStatement,

        @NotBlank
        @Size(max = 100)
        String desiredNextStep,

        @Size(max = 100)
        String preferredRegion,

        @Size(max = 100)
        String officeType,

        @Size(max = 100)
        String experienceLevel,

        @Size(max = 150)
        String publicCareerPreference,

        @Size(max = 150)
        String consultationStyle,

        @Size(max = 300)
        String lawyerSearchPurpose,

        List<@Size(max = 100) String> documents
) {
}
