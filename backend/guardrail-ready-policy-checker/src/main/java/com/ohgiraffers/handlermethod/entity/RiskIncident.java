package com.ohgiraffers.handlermethod.entity;

import com.ohgiraffers.handlermethod.incident.IncidentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "risk_incident")
public class RiskIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String incidentType;

    @Column(nullable = false, length = 20)
    private String severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentStatus status;

    @Column(nullable = false, length = 100)
    private String decision;

    @Column(nullable = false)
    private int riskScore;

    @Column(nullable = false, length = 100)
    private String recommendedAction;

    @Column(nullable = false)
    private Long riskHistoryId;

    @Column(nullable = false, length = 100)
    private String traceId;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected RiskIncident() {
    }

    private RiskIncident(String incidentType,
                         String severity,
                         String decision,
                         int riskScore,
                         String recommendedAction,
                         Long riskHistoryId,
                         String traceId,
                         String message) {
        this.incidentType = incidentType;
        this.severity = severity;
        this.status = IncidentStatus.OPEN;
        this.decision = decision;
        this.riskScore = riskScore;
        this.recommendedAction = recommendedAction;
        this.riskHistoryId = riskHistoryId;
        this.traceId = traceId;
        this.message = message;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static RiskIncident open(String incidentType,
                                    String severity,
                                    String decision,
                                    int riskScore,
                                    String recommendedAction,
                                    Long riskHistoryId,
                                    String traceId,
                                    String message) {
        return new RiskIncident(
                incidentType,
                severity,
                decision,
                riskScore,
                recommendedAction,
                riskHistoryId,
                traceId,
                message
        );
    }

    public void acknowledge() {
        this.status = IncidentStatus.ACKNOWLEDGED;
        this.updatedAt = LocalDateTime.now();
    }

    public void resolve() {
        this.status = IncidentStatus.RESOLVED;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getIncidentType() {
        return incidentType;
    }

    public String getSeverity() {
        return severity;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public String getDecision() {
        return decision;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public Long getRiskHistoryId() {
        return riskHistoryId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
