package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.AgentRiskAnalyzeRequest;
import com.ohgiraffers.handlermethod.dto.AgentRiskAnalyzeResponse;
import com.ohgiraffers.handlermethod.entity.AgentRunHistory;
import com.ohgiraffers.handlermethod.entity.AgentRiskHistory;
import com.ohgiraffers.handlermethod.repository.AgentRunHistoryRepository;
import com.ohgiraffers.handlermethod.repository.AgentRiskHistoryRepository;
import com.ohgiraffers.handlermethod.risk.AgentRiskPolicyRule;
import com.ohgiraffers.handlermethod.risk.AgentRiskDecision;
import com.ohgiraffers.handlermethod.risk.AgentRiskLevel;
import com.ohgiraffers.handlermethod.risk.AgentRiskSignal;
import com.ohgiraffers.handlermethod.risk.RecommendedAction;
import com.ohgiraffers.handlermethod.support.AgentMetricRecorder;
import com.ohgiraffers.handlermethod.support.TraceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AgentRiskDecisionService {

    private final AgentMetricRecorder agentMetricRecorder;
    private final AgentRiskHistoryRepository agentRiskHistoryRepository;
    private final AgentRunHistoryRepository agentRunHistoryRepository;
    private final RiskIncidentService riskIncidentService;

    public AgentRiskDecisionService(AgentMetricRecorder agentMetricRecorder,
                                    AgentRiskHistoryRepository agentRiskHistoryRepository,
                                    AgentRunHistoryRepository agentRunHistoryRepository,
                                    RiskIncidentService riskIncidentService) {
        this.agentMetricRecorder = agentMetricRecorder;
        this.agentRiskHistoryRepository = agentRiskHistoryRepository;
        this.agentRunHistoryRepository = agentRunHistoryRepository;
        this.riskIncidentService = riskIncidentService;
    }

    @Transactional
    public AgentRiskAnalyzeResponse analyze(AgentRiskAnalyzeRequest request) {
        String userInput = valueOrDefault(request == null ? null : request.userInput(), "empty agent request");
        String toolName = valueOrDefault(request == null ? null : request.toolName(), "unknown");
        int retryCount = positiveOrDefault(request == null ? null : request.retryCount(), 0);
        int promptTokens = positiveOrDefault(request == null ? null : request.promptTokens(), 120);
        int completionTokens = positiveOrDefault(request == null ? null : request.completionTokens(), 80);

        boolean policyViolation = flag(request == null ? null : request.policyViolation())
                || AgentRiskPolicyRule.POLICY_VIOLATION.matches(userInput, toolName);
        boolean toolError = flag(request == null ? null : request.toolError())
                || AgentRiskPolicyRule.TOOL_ERROR.matches(userInput, toolName);
        boolean externalApiCall = flag(request == null ? null : request.externalApiCall())
                || AgentRiskPolicyRule.EXTERNAL_API_CALL.matches(userInput, toolName);
        boolean approvalRequired = flag(request == null ? null : request.approvalRequired())
                || AgentRiskPolicyRule.APPROVAL_REQUIRED.matches(userInput, toolName);
        boolean dbWrite = flag(request == null ? null : request.dbWrite())
                || AgentRiskPolicyRule.DB_WRITE.matches(userInput, toolName);

        List<AgentRiskSignal> signals = new ArrayList<>();
        int riskScore = 0;

        if (policyViolation) {
            signals.add(AgentRiskSignal.POLICY_VIOLATION);
            riskScore += 40;
            agentMetricRecorder.recordPolicyViolation("risk_decision_policy_violation");
        }

        if (retryCount > 0) {
            signals.add(AgentRiskSignal.RETRY);
            riskScore += Math.min(retryCount * 8, 25);
            agentMetricRecorder.recordRetryCount(retryCount);
        }

        if (externalApiCall) {
            signals.add(AgentRiskSignal.EXTERNAL_API_CALL);
            riskScore += 25;
            agentMetricRecorder.recordExternalApiCall("risk-decision-external-api", policyViolation ? "risky" : "called");
        }

        if (toolError) {
            signals.add(AgentRiskSignal.TOOL_ERROR);
            riskScore += 15;
            agentMetricRecorder.recordToolError(toolName, "risk_decision_tool_error");
        }

        if (approvalRequired) {
            signals.add(AgentRiskSignal.APPROVAL_REQUIRED);
            riskScore += 20;
            agentMetricRecorder.recordApprovalRequired("risk_decision_approval_boundary");
        }

        if (dbWrite) {
            signals.add(AgentRiskSignal.DB_WRITE);
            riskScore += 15;
            agentMetricRecorder.recordDbWrite("risk-decision-db", policyViolation ? "risky" : "attempted");
        }

        if (signals.isEmpty()) {
            signals.add(AgentRiskSignal.SAFE_REQUEST);
        }

        agentMetricRecorder.recordToolCall(toolName, "risk_decision_analyze");
        agentMetricRecorder.recordTokenCost(promptTokens, completionTokens);

        int boundedRiskScore = Math.min(riskScore, 100);
        AgentRiskLevel riskLevel = riskLevel(boundedRiskScore);
        AgentRiskDecision decision = decision(signals, boundedRiskScore);
        RecommendedAction recommendedAction = recommendedAction(decision, riskLevel);
        String traceId = TraceContext.currentTraceId();
        List<String> signalNames = signals.stream()
                .map(Enum::name)
                .collect(Collectors.toList());

        AgentRiskHistory history = AgentRiskHistory.create(
                userInput,
                toolName,
                decision.name(),
                boundedRiskScore,
                riskLevel.name(),
                String.join(",", signalNames),
                recommendedAction.name(),
                true,
                traceId
        );
        AgentRiskHistory savedHistory = agentRiskHistoryRepository.save(history);
        agentRunHistoryRepository.save(AgentRunHistory.create(
                userInput,
                toolName,
                retryCount,
                promptTokens + completionTokens,
                decision.name(),
                boundedRiskScore,
                traceId
        ));
        riskIncidentService.createIfNeeded(
                decision.name(),
                boundedRiskScore,
                riskLevel.name(),
                recommendedAction.name(),
                savedHistory.getId(),
                traceId
        );

        return new AgentRiskAnalyzeResponse(
                decision.name(),
                boundedRiskScore,
                riskLevel.name(),
                signalNames,
                recommendedAction.name(),
                true,
                savedHistory.getId(),
                traceId
        );
    }

    private AgentRiskDecision decision(List<AgentRiskSignal> signals, int riskScore) {
        if (signals.contains(AgentRiskSignal.POLICY_VIOLATION)
                && signals.contains(AgentRiskSignal.EXTERNAL_API_CALL)
                && signals.contains(AgentRiskSignal.RETRY)) {
            return AgentRiskDecision.HIGH_RISK_AGENT_BEHAVIOR;
        }

        if (signals.contains(AgentRiskSignal.POLICY_VIOLATION)
                && signals.contains(AgentRiskSignal.EXTERNAL_API_CALL)) {
            return AgentRiskDecision.RISKY_EXTERNAL_ACCESS;
        }

        if (signals.contains(AgentRiskSignal.POLICY_VIOLATION)
                && signals.contains(AgentRiskSignal.RETRY)) {
            return AgentRiskDecision.SUSPICIOUS_RETRY;
        }

        if (signals.contains(AgentRiskSignal.APPROVAL_REQUIRED)
                && signals.contains(AgentRiskSignal.RETRY)) {
            return AgentRiskDecision.APPROVAL_BYPASS_RISK;
        }

        if (signals.contains(AgentRiskSignal.TOOL_ERROR)
                && signals.contains(AgentRiskSignal.RETRY)) {
            return AgentRiskDecision.UNSTABLE_TOOL_LOOP;
        }

        if (riskScore >= 60) {
            return AgentRiskDecision.SUSPICIOUS;
        }

        return AgentRiskDecision.SAFE;
    }

    private AgentRiskLevel riskLevel(int riskScore) {
        if (riskScore >= 80) {
            return AgentRiskLevel.HIGH;
        }

        if (riskScore >= 40) {
            return AgentRiskLevel.MEDIUM;
        }

        return AgentRiskLevel.LOW;
    }

    private RecommendedAction recommendedAction(AgentRiskDecision decision, AgentRiskLevel riskLevel) {
        if (decision == AgentRiskDecision.HIGH_RISK_AGENT_BEHAVIOR
                || decision == AgentRiskDecision.RISKY_EXTERNAL_ACCESS) {
            return RecommendedAction.BLOCK_AND_ESCALATE;
        }

        if (decision == AgentRiskDecision.SUSPICIOUS_RETRY) {
            return RecommendedAction.BLOCK;
        }

        if (decision == AgentRiskDecision.APPROVAL_BYPASS_RISK) {
            return RecommendedAction.REQUIRE_APPROVAL;
        }

        if (decision == AgentRiskDecision.UNSTABLE_TOOL_LOOP) {
            return RecommendedAction.DISABLE_TOOL_TEMPORARILY;
        }

        if (riskLevel == AgentRiskLevel.HIGH) {
            return RecommendedAction.CREATE_INCIDENT;
        }

        if (riskLevel == AgentRiskLevel.MEDIUM) {
            return RecommendedAction.REQUIRE_APPROVAL;
        }

        return RecommendedAction.ALLOW;
    }

    private boolean flag(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        if (value == null || value < 0) {
            return defaultValue;
        }

        return value;
    }

    private String valueOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value;
    }
}
