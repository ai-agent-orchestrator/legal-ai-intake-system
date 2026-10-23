package com.ohgiraffers.handlermethod.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LegalIntakeStatusRequest(
        @NotBlank
        @Pattern(regexp = "SUBMITTED|REVIEWED|NEED_MORE_INFO|CONSULTATION_READY|CLOSED")
        String status
) {
}
