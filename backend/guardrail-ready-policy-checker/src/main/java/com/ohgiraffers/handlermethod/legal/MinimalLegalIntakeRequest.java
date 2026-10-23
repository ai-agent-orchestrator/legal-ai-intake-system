package com.ohgiraffers.handlermethod.legal;

import com.ohgiraffers.handlermethod.dto.LegalInputLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record MinimalLegalIntakeRequest(
        @Size(max = 100) String sessionId,
        @Size(max = 100) String inquiryPurpose,
        @Size(max = 50)
        @Pattern(regexp = "CIVIL|CRIMINAL|FAMILY|ELECTRONIC_LITIGATION|UNKNOWN",
                flags = Pattern.Flag.CASE_INSENSITIVE, message = "Unsupported case type")
        String caseType,
        @Size(max = 50) String partyRole,
        @Size(max = 30) String incidentDate,
        @Size(max = 100) String counterpart,
        @Size(max = 100) String amountOrDamage,
        @Size(max = 20) List<@NotBlank @Size(max = 100) String> documents,
        @Size(max = 100) String desiredNextStep,
        @NotBlank @Size(max = LegalInputLimits.MAX_CASE_STORY_CHARS) String coreFact
) {
}
