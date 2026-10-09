package com.ohgiraffers.handlermethod.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "legal_intake_history")
public class LegalIntakeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String caseType;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(nullable = false, length = 300)
    private String claimPurpose;

    @Column(nullable = false)
    private boolean hasEvidence;

    @Column(nullable = false)
    private boolean hasDeadline;

    @Column(nullable = false)
    private boolean opponentKnown;

    @Column(nullable = false)
    private boolean damageAmountKnown;

    @Column(nullable = false, length = 100)
    private String currentStage;

    @Column(nullable = false)
    private boolean urgent;

    @Column(nullable = false, length = 100)
    private String decision;

    @Column(nullable = false)
    private int readinessScore;

    @Column(nullable = false, length = 30)
    private String readinessLevel;

    @Column(nullable = false, length = 500)
    private String signals;

    @Column(nullable = false, length = 120)
    private String recommendedNextStep;

    @Column(nullable = false, length = 100)
    private String traceId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected LegalIntakeHistory() {
    }

    private LegalIntakeHistory(String caseType,
                               String summary,
                               String claimPurpose,
                               boolean hasEvidence,
                               boolean hasDeadline,
                               boolean opponentKnown,
                               boolean damageAmountKnown,
                               String currentStage,
                               boolean urgent,
                               String decision,
                               int readinessScore,
                               String readinessLevel,
                               String signals,
                               String recommendedNextStep,
                               String traceId) {
        this.caseType = caseType;
        this.summary = summary;
        this.claimPurpose = claimPurpose;
        this.hasEvidence = hasEvidence;
        this.hasDeadline = hasDeadline;
        this.opponentKnown = opponentKnown;
        this.damageAmountKnown = damageAmountKnown;
        this.currentStage = currentStage;
        this.urgent = urgent;
        this.decision = decision;
        this.readinessScore = readinessScore;
        this.readinessLevel = readinessLevel;
        this.signals = signals;
        this.recommendedNextStep = recommendedNextStep;
        this.traceId = traceId;
        this.createdAt = LocalDateTime.now();
    }

    public static LegalIntakeHistory create(String caseType,
                                            String summary,
                                            String claimPurpose,
                                            boolean hasEvidence,
                                            boolean hasDeadline,
                                            boolean opponentKnown,
                                            boolean damageAmountKnown,
                                            String currentStage,
                                            boolean urgent,
                                            String decision,
                                            int readinessScore,
                                            String readinessLevel,
                                            String signals,
                                            String recommendedNextStep,
                                            String traceId) {
        return new LegalIntakeHistory(
                caseType,
                summary,
                claimPurpose,
                hasEvidence,
                hasDeadline,
                opponentKnown,
                damageAmountKnown,
                currentStage,
                urgent,
                decision,
                readinessScore,
                readinessLevel,
                signals,
                recommendedNextStep,
                traceId
        );
    }

    public Long getId() {
        return id;
    }

    public String getCaseType() {
        return caseType;
    }

    public String getSummary() {
        return summary;
    }

    public String getClaimPurpose() {
        return claimPurpose;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public String getDecision() {
        return decision;
    }

    public int getReadinessScore() {
        return readinessScore;
    }

    public String getReadinessLevel() {
        return readinessLevel;
    }

    public String getSignals() {
        return signals;
    }

    public String getRecommendedNextStep() {
        return recommendedNextStep;
    }

    public String getTraceId() {
        return traceId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
