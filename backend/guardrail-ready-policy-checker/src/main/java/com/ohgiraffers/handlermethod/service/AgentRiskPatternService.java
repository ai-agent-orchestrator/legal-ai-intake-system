package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.AgentPracticeRequest;
import com.ohgiraffers.handlermethod.dto.AgentPracticeResponse;
import com.ohgiraffers.handlermethod.support.AgentMetricRecorder;
import com.ohgiraffers.handlermethod.support.TraceContext;
import org.springframework.stereotype.Service;

@Service
public class AgentRiskPatternService {

    private final AgentMetricRecorder agentMetricRecorder;

    public AgentRiskPatternService(AgentMetricRecorder agentMetricRecorder) {
        this.agentMetricRecorder = agentMetricRecorder;
    }

    public AgentPracticeResponse policyViolationRetry(AgentPracticeRequest request) {
        String toolName = toolName(request, "database");
        int planSteps = planSteps(request, 4);
        int retryCount = retryCount(request, 3);
        int totalTokens = totalTokens(request, 120, 40);

        agentMetricRecorder.recordToolCall(toolName, "denied_retry");
        agentMetricRecorder.recordPolicyViolation("blocked_sensitive_operation");
        agentMetricRecorder.recordRetryCount(retryCount);
        agentMetricRecorder.recordPlanSteps(planSteps);
        agentMetricRecorder.recordTokenCost(promptTokens(request, 120), completionTokens(request, 40));

        return response(
                "policy-violation-retry",
                "SUSPICIOUS",
                "Policy violation combined with retries. This may indicate repeated attempts around a blocked action.",
                toolName,
                planSteps,
                retryCount,
                totalTokens
        );
    }

    public AgentPracticeResponse toolErrorRetry(AgentPracticeRequest request) {
        String toolName = toolName(request, "search");
        int planSteps = planSteps(request, 5);
        int retryCount = retryCount(request, 3);
        int totalTokens = totalTokens(request, 100, 50);

        agentMetricRecorder.recordToolCall(toolName, "error_retry");
        agentMetricRecorder.recordToolError(toolName, "repeated_tool_failure");
        agentMetricRecorder.recordRetryCount(retryCount);
        agentMetricRecorder.recordPlanSteps(planSteps);
        agentMetricRecorder.recordTokenCost(promptTokens(request, 100), completionTokens(request, 50));

        return response(
                "tool-error-retry",
                "UNSTABLE",
                "Tool error combined with retries. This may indicate repeated calls to an unstable tool.",
                toolName,
                planSteps,
                retryCount,
                totalTokens
        );
    }

    public AgentPracticeResponse externalApiPolicyViolation(AgentPracticeRequest request) {
        String toolName = toolName(request, "external-api");
        int planSteps = planSteps(request, 4);
        int retryCount = retryCount(request, 1);
        int totalTokens = totalTokens(request, 180, 70);

        agentMetricRecorder.recordToolCall(toolName, "external_policy_violation");
        agentMetricRecorder.recordExternalApiCall("mock-llm-provider", "risky");
        agentMetricRecorder.recordPolicyViolation("risky_external_dependency");
        agentMetricRecorder.recordRetryCount(retryCount);
        agentMetricRecorder.recordPlanSteps(planSteps);
        agentMetricRecorder.recordTokenCost(promptTokens(request, 180), completionTokens(request, 70));

        return response(
                "external-api-policy-violation",
                "RISKY_EXTERNAL_ACCESS",
                "External API call combined with policy violation. This may indicate risky movement toward an external dependency.",
                toolName,
                planSteps,
                retryCount,
                totalTokens
        );
    }

    public AgentPracticeResponse approvalRequiredRetry(AgentPracticeRequest request) {
        String toolName = toolName(request, "email");
        int planSteps = planSteps(request, 4);
        int retryCount = retryCount(request, 2);
        int totalTokens = totalTokens(request, 150, 60);

        agentMetricRecorder.recordToolCall(toolName, "approval_retry");
        agentMetricRecorder.recordApprovalRequired("sensitive_action_retry");
        agentMetricRecorder.recordRetryCount(retryCount);
        agentMetricRecorder.recordPlanSteps(planSteps);
        agentMetricRecorder.recordTokenCost(promptTokens(request, 150), completionTokens(request, 60));

        return response(
                "approval-required-retry",
                "APPROVAL_RISK",
                "Approval-required action combined with retries. This may indicate repeated attempts near a human approval boundary.",
                toolName,
                planSteps,
                retryCount,
                totalTokens
        );
    }

    private AgentPracticeResponse response(String mode,
                                           String decision,
                                           String message,
                                           String toolName,
                                           int planSteps,
                                           int retryCount,
                                           int totalTokens) {
        return new AgentPracticeResponse(
                mode,
                decision,
                message,
                toolName,
                planSteps,
                retryCount,
                totalTokens,
                TraceContext.currentTraceId()
        );
    }

    private String toolName(AgentPracticeRequest request, String defaultValue) {
        if (request == null || request.toolName() == null || request.toolName().isBlank()) {
            return defaultValue;
        }

        return request.toolName();
    }

    private int planSteps(AgentPracticeRequest request, int defaultValue) {
        if (request == null || request.planSteps() == null || request.planSteps() <= 0) {
            return defaultValue;
        }

        return request.planSteps();
    }

    private int retryCount(AgentPracticeRequest request, int defaultValue) {
        if (request == null || request.retryCount() == null || request.retryCount() <= 0) {
            return defaultValue;
        }

        return request.retryCount();
    }

    private int promptTokens(AgentPracticeRequest request, int defaultValue) {
        if (request == null || request.promptTokens() == null || request.promptTokens() <= 0) {
            return defaultValue;
        }

        return request.promptTokens();
    }

    private int completionTokens(AgentPracticeRequest request, int defaultValue) {
        if (request == null || request.completionTokens() == null || request.completionTokens() <= 0) {
            return defaultValue;
        }

        return request.completionTokens();
    }

    private int totalTokens(AgentPracticeRequest request, int promptDefault, int completionDefault) {
        return promptTokens(request, promptDefault) + completionTokens(request, completionDefault);
    }
}
