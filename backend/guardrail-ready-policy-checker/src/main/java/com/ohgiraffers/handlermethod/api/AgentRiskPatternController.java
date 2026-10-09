package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.AgentPracticeRequest;
import com.ohgiraffers.handlermethod.dto.AgentPracticeResponse;
import com.ohgiraffers.handlermethod.service.AgentRiskPatternService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/risk-patterns")
public class AgentRiskPatternController {

    private final AgentRiskPatternService agentRiskPatternService;

    public AgentRiskPatternController(AgentRiskPatternService agentRiskPatternService) {
        this.agentRiskPatternService = agentRiskPatternService;
    }

    @PostMapping("/policy-violation-retry")
    public AgentPracticeResponse policyViolationRetry(@RequestBody(required = false) AgentPracticeRequest request) {
        return agentRiskPatternService.policyViolationRetry(request);
    }

    @PostMapping("/tool-error-retry")
    public AgentPracticeResponse toolErrorRetry(@RequestBody(required = false) AgentPracticeRequest request) {
        return agentRiskPatternService.toolErrorRetry(request);
    }

    @PostMapping("/external-api-policy-violation")
    public AgentPracticeResponse externalApiPolicyViolation(@RequestBody(required = false) AgentPracticeRequest request) {
        return agentRiskPatternService.externalApiPolicyViolation(request);
    }

    @PostMapping("/approval-required-retry")
    public AgentPracticeResponse approvalRequiredRetry(@RequestBody(required = false) AgentPracticeRequest request) {
        return agentRiskPatternService.approvalRequiredRetry(request);
    }
}
