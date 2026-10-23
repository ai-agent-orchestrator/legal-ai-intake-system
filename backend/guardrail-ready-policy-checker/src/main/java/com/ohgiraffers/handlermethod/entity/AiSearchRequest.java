package com.ohgiraffers.handlermethod.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_search_request")
public class AiSearchRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long intakeId;

    @Column(nullable = false)
    private Long conditionId;

    @Column(nullable = false, length = 300)
    private String searchPurpose;

    @Column(nullable = false, length = 100)
    private String sourcePolicy;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(length = 36) private String executionId;
    @Column(length = 36) private String executionLease;
    @Column(length = 50) private String executionStage;
    @Column(length = 300) private String generatedQuery;
    @Column(length = 100) private String executionError;
    @Column(length = 100) private String sourceWarningCode;
    private LocalDateTime executionStartedAt;
    private LocalDateTime executionFinishedAt;

    public boolean isExecuting() { return "QUEUED".equals(status) || "RUNNING".equals(status); }
    public void beginExecution(String lease, boolean explanationOnly) {
        if (executionId == null) executionId = java.util.UUID.randomUUID().toString();
        executionLease = lease; status = "QUEUED";
        executionStage = explanationOnly ? "EXPLANATION" : "CONDITION_CHECK";
        executionError = null; executionStartedAt = LocalDateTime.now(); executionFinishedAt = null;
        if (!explanationOnly) sourceWarningCode = null;
    }
    public void advanceExecution(String stage) { status = "RUNNING"; executionStage = stage; }
    public void storeQuery(String query) { generatedQuery = query; }
    public void finishExecution(String result, String error) {
        status = result; executionError = error; executionFinishedAt = LocalDateTime.now();
        if (error == null) executionStage = "FINISHED";
    }
    public void rejectQueuedExecution() { status = "PREVIEW_READY"; executionStage = null; executionLease = null; executionStartedAt = null; }
    public String getExecutionId() { return executionId; }
    public String getExecutionLease() { return executionLease; }
    public String getExecutionStage() { return executionStage; }
    public String getGeneratedQuery() { return generatedQuery; }
    public String getExecutionError() { return executionError; }
    public String getSourceWarningCode() { return sourceWarningCode; }
    public void recordSourceWarning(String code) { sourceWarningCode = code; }
    public LocalDateTime getExecutionStartedAt() { return executionStartedAt; }
    public LocalDateTime getExecutionFinishedAt() { return executionFinishedAt; }

    protected AiSearchRequest() {
    }

    private AiSearchRequest(Long intakeId,
                            Long conditionId,
                            String searchPurpose,
                            String sourcePolicy,
                            String status) {
        this.intakeId = intakeId;
        this.conditionId = conditionId;
        this.searchPurpose = searchPurpose;
        this.sourcePolicy = sourcePolicy;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public static AiSearchRequest create(Long intakeId,
                                         Long conditionId,
                                         String searchPurpose,
                                         String sourcePolicy,
                                         String status) {
        return new AiSearchRequest(intakeId, conditionId, searchPurpose, sourcePolicy, status);
    }

    public Long getId() {
        return id;
    }

    public Long getIntakeId() {
        return intakeId;
    }

    public Long getConditionId() {
        return conditionId;
    }

    public String getSearchPurpose() {
        return searchPurpose;
    }

    public String getSourcePolicy() {
        return sourcePolicy;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
