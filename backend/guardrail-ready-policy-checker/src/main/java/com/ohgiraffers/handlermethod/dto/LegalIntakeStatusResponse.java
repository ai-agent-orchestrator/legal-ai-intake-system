package com.ohgiraffers.handlermethod.dto;

public record LegalIntakeStatusResponse(
        Long id,
        String previousStatus,
        String status,
        boolean changed,
        long version
) {
}
