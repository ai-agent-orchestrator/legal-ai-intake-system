package com.ohgiraffers.handlermethod.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LegalIntakeClassificationRequest(
        @Size(max = 50) String caseType,
        @NotBlank @Size(max = LegalInputLimits.MAX_CASE_STORY_CHARS) String coreStatement
) {
}
