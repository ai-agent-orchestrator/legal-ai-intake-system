package com.ohgiraffers.handlermethod.dto;

import com.ohgiraffers.handlermethod.entity.RiskIncident;

import java.time.LocalDateTime;

public record RiskIncidentResponse(
        Long id,
        String incidentType,
        String severity,
        String status,
        String decision,
        int riskScore,
        String recommendedAction,
        Long riskHistoryId,
        String traceId,
        String message,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static RiskIncidentResponse from(RiskIncident incident) {
        return new RiskIncidentResponse(
                incident.getId(),
                incident.getIncidentType(),
                incident.getSeverity(),
                incident.getStatus().name(),
                incident.getDecision(),
                incident.getRiskScore(),
                incident.getRecommendedAction(),
                incident.getRiskHistoryId(),
                incident.getTraceId(),
                incident.getMessage(),
                incident.getCreatedAt(),
                incident.getUpdatedAt()
        );
    }
}
