package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.AgentRiskHistoryResponse;
import com.ohgiraffers.handlermethod.dto.AgentRiskSummaryResponse;
import com.ohgiraffers.handlermethod.service.AgentRiskHistoryQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent/risk")
public class AgentRiskHistoryController {

    private final AgentRiskHistoryQueryService agentRiskHistoryQueryService;

    public AgentRiskHistoryController(AgentRiskHistoryQueryService agentRiskHistoryQueryService) {
        this.agentRiskHistoryQueryService = agentRiskHistoryQueryService;
    }

    @GetMapping("/history")
    public List<AgentRiskHistoryResponse> history(@RequestParam(required = false) String riskLevel,
                                                  @RequestParam(required = false) String decision) {
        return agentRiskHistoryQueryService.findRecent(riskLevel, decision);
    }

    @GetMapping("/summary")
    public AgentRiskSummaryResponse summary() {
        return agentRiskHistoryQueryService.summarize();
    }
}
