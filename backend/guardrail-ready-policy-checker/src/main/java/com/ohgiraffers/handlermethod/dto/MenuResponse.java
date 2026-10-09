package com.ohgiraffers.handlermethod.dto;

public record MenuResponse(
        String name,
        int price,
        int categoryCode,
        String orderableStatus,
        String message,
        String traceId
) {
}
