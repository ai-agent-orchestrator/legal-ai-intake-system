package com.ohgiraffers.handlermethod.dto;

public record ApiStatusResponse(
        String status,
        String service,
        String javaVersion,
        String thread,
        String observability
) {
}
