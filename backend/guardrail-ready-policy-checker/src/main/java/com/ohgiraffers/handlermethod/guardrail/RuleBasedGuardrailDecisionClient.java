package com.ohgiraffers.handlermethod.guardrail;

import com.ohgiraffers.handlermethod.dto.AgentRiskAnalyzeRequest;
import com.ohgiraffers.handlermethod.risk.AgentRiskPolicyRule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RuleBasedGuardrailDecisionClient implements GuardrailDecisionClient {

    @Override
    public GuardrailDecisionResult check(AgentRiskAnalyzeRequest request) {
        String userInput = request == null ? "" : request.userInput();
        String toolName = request == null ? "" : request.toolName();
        List<String> signals = new ArrayList<>();

        if (AgentRiskPolicyRule.POLICY_VIOLATION.matches(userInput, toolName)) {
            signals.add("POLICY_VIOLATION");
        }
        if (AgentRiskPolicyRule.EXTERNAL_API_CALL.matches(userInput, toolName)) {
            signals.add("EXTERNAL_API_CALL");
        }
        if (AgentRiskPolicyRule.APPROVAL_REQUIRED.matches(userInput, toolName)) {
            signals.add("APPROVAL_REQUIRED");
        }

        if (signals.contains("POLICY_VIOLATION") && signals.contains("EXTERNAL_API_CALL")) {
            return new GuardrailDecisionResult("RISKY_EXTERNAL_ACCESS", signals, "BLOCK_AND_ESCALATE", true);
        }
        if (signals.contains("POLICY_VIOLATION")) {
            return new GuardrailDecisionResult("SUSPICIOUS_RETRY", signals, "BLOCK", true);
        }
        if (signals.contains("APPROVAL_REQUIRED")) {
            return new GuardrailDecisionResult("APPROVAL_BYPASS_RISK", signals, "REQUIRE_APPROVAL", true);
        }

        return new GuardrailDecisionResult("SAFE", signals.isEmpty() ? List.of("SAFE_REQUEST") : signals, "ALLOW", true);
    }
}
