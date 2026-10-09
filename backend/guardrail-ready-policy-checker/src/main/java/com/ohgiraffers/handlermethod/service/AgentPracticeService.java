package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.AgentPracticeRequest;
import com.ohgiraffers.handlermethod.dto.AgentPracticeResponse;
import com.ohgiraffers.handlermethod.support.AgentMetricRecorder;
import com.ohgiraffers.handlermethod.support.TraceContext;
import org.springframework.stereotype.Service;

@Service
public class AgentPracticeService {

    private final AgentMetricRecorder agentMetricRecorder;

    public AgentPracticeService(AgentMetricRecorder agentMetricRecorder) {
        this.agentMetricRecorder = agentMetricRecorder;
    }

    public AgentPracticeResponse run(AgentPracticeRequest request) {
        String toolName = toolName(request);
        int planSteps = planSteps(request, 3);
        int retryCount = retryCount(request, 0);
        int totalTokens = totalTokens(request, 120, 80);

        agentMetricRecorder.recordToolCall(toolName, "success");
        agentMetricRecorder.recordPlanSteps(planSteps);
        agentMetricRecorder.recordRetryCount(retryCount);
        agentMetricRecorder.recordTokenCost(promptTokens(request, 120), completionTokens(request, 80));

        return response(
                "run",
                "ALLOWED",
                "Simulated agent run completed.",
                toolName,
                planSteps,
                retryCount,
                totalTokens
        );
    }

    public AgentPracticeResponse toolError(AgentPracticeRequest request) {
        String toolName = toolName(request);
        int planSteps = planSteps(request, 2);
        int retryCount = retryCount(request, 1);
        int totalTokens = totalTokens(request, 80, 20);

        agentMetricRecorder.recordToolCall(toolName, "error");
        agentMetricRecorder.recordToolError(toolName, "simulated_tool_failure");
        agentMetricRecorder.recordPlanSteps(planSteps);
        agentMetricRecorder.recordRetryCount(retryCount);
        agentMetricRecorder.recordTokenCost(promptTokens(request, 80), completionTokens(request, 20));

        return response(
                "tool-error",
                "FAILED",
                "Simulated agent tool failure recorded.",
                toolName,
                planSteps,
                retryCount,
                totalTokens
        );
    }

    public AgentPracticeResponse retry(AgentPracticeRequest request) {
        String toolName = toolName(request);
        int planSteps = planSteps(request, 5);
        int retryCount = retryCount(request, 3);
        int totalTokens = totalTokens(request, 140, 90);

        agentMetricRecorder.recordToolCall(toolName, "retried");
        agentMetricRecorder.recordPlanSteps(planSteps);
        agentMetricRecorder.recordRetryCount(retryCount);
        agentMetricRecorder.recordTokenCost(promptTokens(request, 140), completionTokens(request, 90));

        return response(
                "retry",
                "RETRIED",
                "Simulated agent retry behavior recorded.",
                toolName,
                planSteps,
                retryCount,
                totalTokens
        );
    }

    public AgentPracticeResponse approval(AgentPracticeRequest request) {
        String toolName = toolName(request);
        int planSteps = planSteps(request, 4);
        int retryCount = retryCount(request, 0);
        int totalTokens = totalTokens(request, 160, 60);

        agentMetricRecorder.recordToolCall(toolName, "approval_required");
        agentMetricRecorder.recordApprovalRequired("sensitive_action");
        agentMetricRecorder.recordPlanSteps(planSteps);
        agentMetricRecorder.recordRetryCount(retryCount);
        agentMetricRecorder.recordTokenCost(promptTokens(request, 160), completionTokens(request, 60));

        return response(
                "approval",
                "APPROVAL_REQUIRED",
                "Simulated sensitive action requires human approval.",
                toolName,
                planSteps,
                retryCount,
                totalTokens
        );
    }

    public AgentPracticeResponse policyViolation(AgentPracticeRequest request) {
        String toolName = toolName(request);
        int planSteps = planSteps(request, 1);
        int retryCount = retryCount(request, 0);
        int totalTokens = totalTokens(request, 60, 10);

        agentMetricRecorder.recordToolCall(toolName, "denied");
        agentMetricRecorder.recordPolicyViolation("mock_guardrail_policy");
        agentMetricRecorder.recordPlanSteps(planSteps);
        agentMetricRecorder.recordRetryCount(retryCount);
        agentMetricRecorder.recordTokenCost(promptTokens(request, 60), completionTokens(request, 10));

        return response(
                "policy-violation",
                "DENIED",
                "Simulated guardrail-ready policy violation recorded.",
                toolName,
                planSteps,
                retryCount,
                totalTokens
        );
    }

    public AgentPracticeResponse externalApi(AgentPracticeRequest request) {
        String toolName = toolName(request);
        int planSteps = planSteps(request, 3);
        int retryCount = retryCount(request, 0);
        int totalTokens = totalTokens(request, 200, 120);

        agentMetricRecorder.recordToolCall(toolName, "external_api");
        agentMetricRecorder.recordExternalApiCall("mock-llm-provider", "success");
        agentMetricRecorder.recordPlanSteps(planSteps);
        agentMetricRecorder.recordRetryCount(retryCount);
        agentMetricRecorder.recordTokenCost(promptTokens(request, 200), completionTokens(request, 120));

        return response(
                "external-api",
                "ALLOWED",
                "Simulated external API call recorded.",
                toolName,
                planSteps,
                retryCount,
                totalTokens
        );
    }

    public AgentPracticeResponse dbWrite(AgentPracticeRequest request) {
        String toolName = toolName(request);
        int planSteps = planSteps(request, 2);
        int retryCount = retryCount(request, 0);
        int totalTokens = totalTokens(request, 100, 40);

        agentMetricRecorder.recordToolCall(toolName, "db_write");
        agentMetricRecorder.recordDbWrite("agent_memory", "success");
        agentMetricRecorder.recordPlanSteps(planSteps);
        agentMetricRecorder.recordRetryCount(retryCount);
        agentMetricRecorder.recordTokenCost(promptTokens(request, 100), completionTokens(request, 40));

        return response(
                "db-write",
                "ALLOWED",
                "Simulated agent database write recorded.",
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

    private String toolName(AgentPracticeRequest request) {
        if (request == null || request.toolName() == null || request.toolName().isBlank()) {
            return "search";
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
        if (request == null || request.retryCount() == null || request.retryCount() < 0) {
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
