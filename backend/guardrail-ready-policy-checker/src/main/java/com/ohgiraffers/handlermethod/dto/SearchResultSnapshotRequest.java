package com.ohgiraffers.handlermethod.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import com.ohgiraffers.handlermethod.validation.HttpResultUrl;

public record SearchResultSnapshotRequest(
        @NotBlank
        @Size(max = 100)
        String sourceType,

        @NotBlank
        @Size(max = 300)
        String title,

        @NotBlank
        @Size(max = 1000)
        @HttpResultUrl
        String url,

        @Size(max = 1000)
        String snippet,

        @Positive Integer displayOrder
) {
}
