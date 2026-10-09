package com.ohgiraffers.handlermethod.dto;

import com.ohgiraffers.handlermethod.entity.AgentJobHistory;

import java.time.LocalDateTime;

public record AgentJobStatusResponse(
        Long jobId,
        String status,
        int progress,
        String userInput,
        String toolName,
        int retryCount,
        String decision,
        Integer riskScore,
        String riskLevel,
        String recommendedAction,
        String errorMessage,
        String traceId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AgentJobStatusResponse from(AgentJobHistory history) {
        return new AgentJobStatusResponse(
                history.getId(),
                history.getStatus().name(),
                history.getProgress(),
                history.getUserInput(),
                history.getToolName(),
                history.getRetryCount(),
                history.getDecision(),
                history.getRiskScore(),
                history.getRiskLevel(),
                history.getRecommendedAction(),
                history.getErrorMessage(),
                history.getTraceId(),
                history.getCreatedAt(),
                history.getUpdatedAt()
        );
    }
}
