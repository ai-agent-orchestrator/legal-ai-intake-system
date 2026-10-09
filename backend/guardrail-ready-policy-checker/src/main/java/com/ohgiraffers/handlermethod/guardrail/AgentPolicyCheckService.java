package com.ohgiraffers.handlermethod.guardrail;

import com.ohgiraffers.handlermethod.dto.AgentPracticeRequest;
import com.ohgiraffers.handlermethod.support.AgentMetricRecorder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AgentPolicyCheckService {

    private final AgentMetricRecorder agentMetricRecorder;

    public AgentPolicyCheckService(AgentMetricRecorder agentMetricRecorder) {
        this.agentMetricRecorder = agentMetricRecorder;
    }

    public AgentPolicyDecision check(AgentPracticeRequest request) {
        String userInput = normalizeInput(request);
        AgentPolicyDecision decision = decide(userInput);

        agentMetricRecorder.recordPolicyCheck(decision.decision().name(), decision.policyName());

        if (decision.decision() == AgentPolicyDecisionType.ALLOWED) {
            agentMetricRecorder.recordPolicyAllowed(decision.policyName());
        }

        if (decision.decision() == AgentPolicyDecisionType.APPROVAL_REQUIRED) {
            agentMetricRecorder.recordApprovalRequired(decision.policyName());
        }

        if (decision.decision() == AgentPolicyDecisionType.DENIED) {
            agentMetricRecorder.recordPolicyViolation(decision.policyName());
        }

        return decision;
    }

    private AgentPolicyDecision decide(String userInput) {
        if (containsAny(userInput, "delete all", "drop table", "truncate", "remove all", "customer records")) {
            return new AgentPolicyDecision(
                    AgentPolicyDecisionType.DENIED,
                    "dangerous_database_operation",
                    "Dangerous database or bulk customer-data operation requires blocking.",
                    true
            );
        }

        if (containsAny(userInput, "send", "email", "customer report", "sensitive report", "personal data")) {
            return new AgentPolicyDecision(
                    AgentPolicyDecisionType.APPROVAL_REQUIRED,
                    "sensitive_action",
                    "Sensitive outbound action requires explicit human approval.",
                    true
            );
        }

        if (containsAny(userInput, "external api", "webhook", "upload", "http://", "https://")) {
            return new AgentPolicyDecision(
                    AgentPolicyDecisionType.APPROVAL_REQUIRED,
                    "external_dependency_risk",
                    "External dependency access requires approval before outbound execution.",
                    true
            );
        }

        return new AgentPolicyDecision(
                AgentPolicyDecisionType.ALLOWED,
                "safe_request",
                "No mock guardrail policy was triggered.",
                true
        );
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private String normalizeInput(AgentPracticeRequest request) {
        if (request == null || request.userInput() == null) {
            return "";
        }

        return request.userInput().toLowerCase(Locale.ROOT);
    }
}
