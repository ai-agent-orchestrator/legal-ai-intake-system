package com.ohgiraffers.handlermethod.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record PracticeMenuCreateRequest(
        @NotBlank(message = "name is required")
        String name,

        @Min(value = 1, message = "price must be greater than 0")
        int price
) {
}
