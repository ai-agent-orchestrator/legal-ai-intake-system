package com.ohgiraffers.handlermethod.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AiSearchRequestResponse(
        Long id,
        Long intakeId,
        Long conditionId,
        String searchPurpose,
        String sourcePolicy,
        String status,
        String searchQuery,
        List<SearchResultSnapshotResponse> results,
        LocalDateTime createdAt,
        SearchExecutionResponse execution
) {
    public AiSearchRequestResponse(Long id, Long intakeId, Long conditionId, String searchPurpose, String sourcePolicy,
            String status, String searchQuery, List<SearchResultSnapshotResponse> results, LocalDateTime createdAt) {
        this(id, intakeId, conditionId, searchPurpose, sourcePolicy, status, searchQuery, results, createdAt, null);
    }
}
