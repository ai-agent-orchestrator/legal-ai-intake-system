package com.ohgiraffers.handlermethod.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiChatRequest(
        @NotBlank(message = "message is required")
        @Size(max = LegalInputLimits.MAX_CASE_STORY_CHARS, message = "message must be 100 characters or less")
        String message,
        String sessionId
) {
}
