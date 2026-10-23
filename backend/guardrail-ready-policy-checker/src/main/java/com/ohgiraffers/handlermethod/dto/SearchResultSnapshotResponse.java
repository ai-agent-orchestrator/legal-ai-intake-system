package com.ohgiraffers.handlermethod.dto;

import java.time.LocalDateTime;

public record SearchResultSnapshotResponse(
        Long id,
        Long searchRequestId,
        String sourceType,
        String title,
        String url,
        String snippet,
        int displayOrder,
        LocalDateTime createdAt,
        String explanation
) {
    public SearchResultSnapshotResponse(Long id, Long searchRequestId, String sourceType, String title, String url,
            String snippet, int displayOrder, LocalDateTime createdAt) {
        this(id, searchRequestId, sourceType, title, url, snippet, displayOrder, createdAt, null);
    }
}
