package com.ohgiraffers.handlermethod.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_risk_history")
public class AgentRiskHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String userInput;

    @Column(nullable = false, length = 100)
    private String toolName;

    @Column(nullable = false, length = 100)
    private String decision;

    @Column(nullable = false)
    private int riskScore;

    @Column(nullable = false, length = 30)
    private String riskLevel;

    @Column(nullable = false, length = 500)
    private String signals;

    @Column(nullable = false, length = 100)
    private String recommendedAction;

    @Column(nullable = false)
    private boolean guardrailReady;

    @Column(nullable = false, length = 100)
    private String traceId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected AgentRiskHistory() {
    }

    private AgentRiskHistory(String userInput,
                             String toolName,
                             String decision,
                             int riskScore,
                             String riskLevel,
                             String signals,
                             String recommendedAction,
                             boolean guardrailReady,
                             String traceId,
                             LocalDateTime createdAt) {
        this.userInput = userInput;
        this.toolName = toolName;
        this.decision = decision;
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.signals = signals;
        this.recommendedAction = recommendedAction;
        this.guardrailReady = guardrailReady;
        this.traceId = traceId;
        this.createdAt = createdAt;
    }

    public static AgentRiskHistory create(String userInput,
                                          String toolName,
                                          String decision,
                                          int riskScore,
                                          String riskLevel,
                                          String signals,
                                          String recommendedAction,
                                          boolean guardrailReady,
                                          String traceId) {
        return new AgentRiskHistory(
                userInput,
                toolName,
                decision,
                riskScore,
                riskLevel,
                signals,
                recommendedAction,
                guardrailReady,
                traceId,
                LocalDateTime.now()
        );
    }

    public Long getId() {
        return id;
    }

    public String getUserInput() {
        return userInput;
    }

    public String getToolName() {
        return toolName;
    }

    public String getDecision() {
        return decision;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getSignals() {
        return signals;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public boolean isGuardrailReady() {
        return guardrailReady;
    }

    public String getTraceId() {
        return traceId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
