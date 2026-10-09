package com.ohgiraffers.handlermethod.dto;

public record ChatResponse(
        String userId,
        String model,
        String answer,
        String thread,
        String traceId
) {
}
