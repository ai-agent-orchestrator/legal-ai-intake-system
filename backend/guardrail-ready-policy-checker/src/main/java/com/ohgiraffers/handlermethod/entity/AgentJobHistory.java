package com.ohgiraffers.handlermethod.entity;

import com.ohgiraffers.handlermethod.job.AgentJobStatus;
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
@Table(name = "agent_job_history")
public class AgentJobHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AgentJobStatus status;

    @Column(nullable = false)
    private int progress;

    @Column(nullable = false, length = 500)
    private String userInput;

    @Column(nullable = false, length = 100)
    private String toolName;

    @Column(nullable = false)
    private int retryCount;

    @Column(length = 100)
    private String decision;

    @Column
    private Integer riskScore;

    @Column(length = 30)
    private String riskLevel;

    @Column(length = 100)
    private String recommendedAction;

    @Column(length = 500)
    private String errorMessage;

    @Column(nullable = false, length = 100)
    private String traceId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected AgentJobHistory() {
    }

    private AgentJobHistory(String userInput, String toolName, int retryCount, String traceId) {
        this.status = AgentJobStatus.PENDING;
        this.progress = 0;
        this.userInput = userInput;
        this.toolName = toolName;
        this.retryCount = retryCount;
        this.traceId = traceId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static AgentJobHistory createPending(String userInput, String toolName, int retryCount, String traceId) {
        return new AgentJobHistory(userInput, toolName, retryCount, traceId);
    }

    public void markRunning() {
        this.status = AgentJobStatus.RUNNING;
        this.progress = 50;
        this.updatedAt = LocalDateTime.now();
    }

    public void markCompleted(String decision, int riskScore, String riskLevel, String recommendedAction) {
        this.status = AgentJobStatus.COMPLETED;
        this.progress = 100;
        this.decision = decision;
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.recommendedAction = recommendedAction;
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = AgentJobStatus.FAILED;
        this.progress = 100;
        this.errorMessage = errorMessage;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public AgentJobStatus getStatus() {
        return status;
    }

    public int getProgress() {
        return progress;
    }

    public String getUserInput() {
        return userInput;
    }

    public String getToolName() {
        return toolName;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getDecision() {
        return decision;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getTraceId() {
        return traceId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
