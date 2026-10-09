package com.ohgiraffers.handlermethod.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        String code,
        String message,
        int status,
        String path,
        String traceId,
        Map<String, String> fieldErrors,
        Instant timestamp
) {
}
