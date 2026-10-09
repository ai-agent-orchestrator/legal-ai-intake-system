package com.ohgiraffers.handlermethod.dto;

import com.ohgiraffers.handlermethod.entity.SecurityEventHistory;

import java.time.LocalDateTime;

public record SecurityEventHistoryResponse(
        Long id,
        String eventType,
        String method,
        String uri,
        String status,
        String username,
        LocalDateTime createdAt
) {

    public static SecurityEventHistoryResponse from(SecurityEventHistory history) {
        return new SecurityEventHistoryResponse(
                history.getId(),
                history.getEventType(),
                history.getMethod(),
                history.getUri(),
                history.getStatus(),
                history.getUsername(),
                history.getCreatedAt()
        );
    }
}
