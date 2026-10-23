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

    public boolean acknowledge() {
        return changeStatus(IncidentStatus.ACKNOWLEDGED);
    }

    public boolean resolve() {
        return changeStatus(IncidentStatus.RESOLVED);
    }

    /**
     * 허용된 전이만 반영한다. 같은 상태면 updatedAt도 그대로 두고 false를 돌려준다.
     * 종결(RESOLVED)된 건을 확인됨(ACKNOWLEDGED)으로 되돌리는 등 역방향 전이는 IllegalStateException.
     */
    public boolean changeStatus(IncidentStatus next) {
        if (status == next) {
            return false;
        }
        if (!status.canChangeTo(next)) {
            throw new IllegalStateException("Cannot change incident status from " + status + " to " + next);
        }
        this.status = next;
        this.updatedAt = LocalDateTime.now();
        return true;
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
