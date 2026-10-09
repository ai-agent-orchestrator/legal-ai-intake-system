package com.ohgiraffers.handlermethod.dto;

import jakarta.validation.constraints.NotBlank;

public record PracticeMenuUpdateRequest(
        @NotBlank(message = "name is required")
        String name
) {
}
