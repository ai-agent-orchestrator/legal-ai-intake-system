package com.ohgiraffers.handlermethod.dto;

import jakarta.validation.constraints.Size;

public record AiSearchRequestCreateRequest(
        @Size(max = 300)
        String searchPurpose,

        @Size(max = 100)
        String sourcePolicy
) {
}
