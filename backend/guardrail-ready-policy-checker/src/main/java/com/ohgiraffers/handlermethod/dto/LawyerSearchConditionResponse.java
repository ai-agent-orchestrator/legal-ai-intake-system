package com.ohgiraffers.handlermethod.dto;

import java.time.LocalDateTime;

public record LawyerSearchConditionResponse(
        Long id,
        Long intakeId,
        String legalField,
        String disputeType,
        String preferredRegion,
        String officeType,
        String experienceLevel,
        String publicCareerPreference,
        String consultationStyle,
        String sourcePolicy,
        String searchQuery,
        LocalDateTime createdAt
) {
}
