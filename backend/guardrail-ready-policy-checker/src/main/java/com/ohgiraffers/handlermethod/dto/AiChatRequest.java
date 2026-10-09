package com.ohgiraffers.handlermethod.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiChatRequest(
        @NotBlank(message = "message is required")
        @Size(max = 4000, message = "message must be 4000 characters or less")
        String message
) {
}
