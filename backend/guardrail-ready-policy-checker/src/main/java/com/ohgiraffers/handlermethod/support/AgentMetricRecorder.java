package com.ohgiraffers.handlermethod.support;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AgentMetricRecorder {

    private final MeterRegistry meterRegistry;

    public AgentMetricRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordToolCall(String toolName, String outcome) {
        Counter.builder("agent.tool.calls")
                .description("Total number of agent tool calls")
                .tag("tool", normalize(toolName))
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    public void recordToolError(String toolName, String errorType) {
        Counter.builder("agent.tool.errors")
                .description("Total number of failed agent tool calls")
                .tag("tool", normalize(toolName))
                .tag("error", errorType)
                .register(meterRegistry)
                .increment();
    }

    public void recordPlanSteps(int planSteps) {
        Counter.builder("agent.plan.steps")
                .description("Total number of agent plan steps")
                .register(meterRegistry)
                .increment(Math.max(planSteps, 0));

        DistributionSummary.builder("agent.plan.steps.per.request")
                .description("Agent plan step count per request")
                .register(meterRegistry)
                .record(Math.max(planSteps, 0));
    }

    public void recordRetryCount(int retryCount) {
        Counter.builder("agent.retry.count")
                .description("Total number of agent retries")
                .register(meterRegistry)
                .increment(Math.max(retryCount, 0));
    }

    public void recordExternalApiCall(String provider, String outcome) {
        Counter.builder("agent.external.api.calls")
                .description("Total number of simulated agent external API calls")
                .tag("provider", normalize(provider))
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    public void recordDbWrite(String target, String outcome) {
        Counter.builder("agent.db.write")
                .description("Total number of simulated agent database writes")
                .tag("target", normalize(target))
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    public void recordTokenCost(int promptTokens, int completionTokens) {
        int safePromptTokens = Math.max(promptTokens, 0);
        int safeCompletionTokens = Math.max(completionTokens, 0);

        Counter.builder("agent.cost.tokens")
                .description("Total number of simulated agent tokens")
                .tag("type", "prompt")
                .register(meterRegistry)
                .increment(safePromptTokens);

        Counter.builder("agent.cost.tokens")
                .description("Total number of simulated agent tokens")
                .tag("type", "completion")
                .register(meterRegistry)
                .increment(safeCompletionTokens);
    }

    public void recordApprovalRequired(String reason) {
        Counter.builder("agent.approval.required")
                .description("Total number of agent requests requiring human approval")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    public void recordJobStarted() {
        Counter.builder("agent.jobs.started")
                .description("Total number of async agent risk jobs started")
                .register(meterRegistry)
                .increment();
    }

    public void recordJobCompleted(String decision, String riskLevel) {
        Counter.builder("agent.jobs.completed")
                .description("Total number of async agent risk jobs completed")
                .tag("decision", normalize(decision))
                .tag("risk_level", normalize(riskLevel))
                .register(meterRegistry)
                .increment();
    }

    public void recordJobFailed(String errorType) {
        Counter.builder("agent.jobs.failed")
                .description("Total number of async agent risk jobs failed")
                .tag("error", normalize(errorType))
                .register(meterRegistry)
                .increment();
    }

    public void recordPolicyCheck(String decision, String policyName) {
        Counter.builder("agent.policy.check")
                .description("Total number of guardrail-ready agent policy checks")
                .tag("decision", normalize(decision))
                .tag("policy", normalize(policyName))
                .register(meterRegistry)
                .increment();
    }

    public void recordPolicyAllowed(String policyName) {
        Counter.builder("agent.policy.allowed")
                .description("Total number of allowed agent policy checks")
                .tag("policy", normalize(policyName))
                .register(meterRegistry)
                .increment();
    }

    public void recordPolicyViolation(String policyName) {
        Counter.builder("agent.policy.violation")
                .description("Total number of simulated agent policy violations")
                .tag("policy", policyName)
                .register(meterRegistry)
                .increment();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }

        return value;
    }
}
