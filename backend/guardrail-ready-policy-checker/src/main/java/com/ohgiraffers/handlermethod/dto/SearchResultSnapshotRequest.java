package com.ohgiraffers.handlermethod.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SearchResultSnapshotRequest(
        @NotBlank
        @Size(max = 100)
        String sourceType,

        @NotBlank
        @Size(max = 300)
        String title,

        @NotBlank
        @Size(max = 1000)
        String url,

        @Size(max = 1000)
        String snippet,

        Integer displayOrder
) {
}
