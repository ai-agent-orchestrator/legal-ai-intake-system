package com.ohgiraffers.handlermethod.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank(message = "userId is required")
        String userId,

        @NotBlank(message = "message is required")
        String message,

        @NotBlank(message = "model is required")
        String model
) {
}
