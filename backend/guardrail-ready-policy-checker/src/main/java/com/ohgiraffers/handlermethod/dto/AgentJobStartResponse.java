package com.ohgiraffers.handlermethod.dto;

public record AgentJobStartResponse(
        Long jobId,
        String status,
        String message,
        String traceId
) {
}
