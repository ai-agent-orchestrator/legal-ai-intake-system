package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.AgentRiskAnalyzeRequest;
import com.ohgiraffers.handlermethod.dto.AgentRiskAnalyzeResponse;
import com.ohgiraffers.handlermethod.service.AgentRiskDecisionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/risk")
public class AgentRiskDecisionController {

    private final AgentRiskDecisionService agentRiskDecisionService;

    public AgentRiskDecisionController(AgentRiskDecisionService agentRiskDecisionService) {
        this.agentRiskDecisionService = agentRiskDecisionService;
    }

    @PostMapping("/analyze")
    public AgentRiskAnalyzeResponse analyze(@RequestBody AgentRiskAnalyzeRequest request) {
        return agentRiskDecisionService.analyze(request);
    }
}
