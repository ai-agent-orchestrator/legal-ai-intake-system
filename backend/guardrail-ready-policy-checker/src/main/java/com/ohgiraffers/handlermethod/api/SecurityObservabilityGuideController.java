package com.ohgiraffers.handlermethod.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/security-observability")
public class SecurityObservabilityGuideController {

    @GetMapping("/guide")
    public Map<String, Object> guide() {
        return Map.of(
                "goal", "Detect suspicious AI agent behavior through metric combinations.",
                "thesis", "Policy is ontology plus security doctrine.",
                "policyModel", Map.of(
                        "ontology", "Identify the domain object, action, actor, tool, and data sensitivity.",
                        "securityDoctrine", "Decide allowed, denied, approval required, escalation, and incident handling."
                ),
                "practiceEndpoints", List.of(
                        "POST /api/agent/policy-check",
                        "POST /api/agent/risk-patterns/policy-violation-retry",
                        "POST /api/agent/risk-patterns/tool-error-retry",
                        "POST /api/agent/risk-patterns/external-api-policy-violation",
                        "POST /api/agent/risk-patterns/approval-required-retry"
                ),
                "metricCombinations", List.of(
                        "policy violation + retry",
                        "tool error + retry",
                        "external API call + policy violation",
                        "approval required + retry"
                ),
                "prometheus", List.of(
                        "increase(agent_policy_violation_total[5m])",
                        "increase(agent_retry_count_total[5m])",
                        "sum by (decision, policy) (increase(agent_policy_check_total[5m]))",
                        "increase(agent_external_api_calls_total[5m])",
                        "increase(agent_approval_required_total[5m])"
                ),
                "futureGuardrail", "Replace the mock AgentPolicyCheckService rules with a NVIDIA NeMo Guardrails adapter."
        );
    }
}
