package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.AgentPolicyCheckResponse;
import com.ohgiraffers.handlermethod.dto.AgentPracticeRequest;
import com.ohgiraffers.handlermethod.guardrail.AgentPolicyCheckService;
import com.ohgiraffers.handlermethod.guardrail.AgentPolicyDecision;
import com.ohgiraffers.handlermethod.support.TraceContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentPolicyCheckController {

    private final AgentPolicyCheckService agentPolicyCheckService;

    public AgentPolicyCheckController(AgentPolicyCheckService agentPolicyCheckService) {
        this.agentPolicyCheckService = agentPolicyCheckService;
    }

    @PostMapping("/policy-check")
    public AgentPolicyCheckResponse policyCheck(@RequestBody(required = false) AgentPracticeRequest request) {
        AgentPolicyDecision decision = agentPolicyCheckService.check(request);

        return new AgentPolicyCheckResponse(
                decision.decision().name(),
                decision.policyName(),
                decision.reason(),
                decision.allowed(),
                decision.guardrailReady(),
                TraceContext.currentTraceId()
        );
    }
}
