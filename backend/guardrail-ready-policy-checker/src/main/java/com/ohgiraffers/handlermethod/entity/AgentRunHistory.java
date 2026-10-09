package com.ohgiraffers.handlermethod.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_run_history")
public class AgentRunHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String userInput;

    @Column(nullable = false, length = 100)
    private String toolName;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private int totalTokens;

    @Column(nullable = false, length = 100)
    private String decision;

    @Column(nullable = false)
    private int riskScore;

    @Column(nullable = false, length = 100)
    private String traceId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected AgentRunHistory() {
    }

    private AgentRunHistory(String userInput,
                            String toolName,
                            int retryCount,
                            int totalTokens,
                            String decision,
                            int riskScore,
                            String traceId,
                            LocalDateTime createdAt) {
        this.userInput = userInput;
        this.toolName = toolName;
        this.retryCount = retryCount;
        this.totalTokens = totalTokens;
        this.decision = decision;
        this.riskScore = riskScore;
        this.traceId = traceId;
        this.createdAt = createdAt;
    }

    public static AgentRunHistory create(String userInput,
                                         String toolName,
                                         int retryCount,
                                         int totalTokens,
                                         String decision,
                                         int riskScore,
                                         String traceId) {
        return new AgentRunHistory(
                userInput,
                toolName,
                retryCount,
                totalTokens,
                decision,
                riskScore,
                traceId,
                LocalDateTime.now()
        );
    }

    public Long getId() {
        return id;
    }
}
