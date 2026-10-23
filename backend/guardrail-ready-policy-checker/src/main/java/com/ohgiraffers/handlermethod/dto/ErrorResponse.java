package com.ohgiraffers.handlermethod.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        String code,
        String message,
        int status,
        String path,
        String traceId,
        Map<String, String> fieldErrors,
        Instant timestamp,
        /* 409 응답 등에서 화면이 바로 갱신할 수 있도록 현재 서버 상태를 담는다. */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        Map<String, Object> details
) {
    public ErrorResponse(String code, String message, int status, String path, String traceId,
                         Map<String, String> fieldErrors, Instant timestamp) {
        this(code, message, status, path, traceId, fieldErrors, timestamp, Map.of());
    }
}
