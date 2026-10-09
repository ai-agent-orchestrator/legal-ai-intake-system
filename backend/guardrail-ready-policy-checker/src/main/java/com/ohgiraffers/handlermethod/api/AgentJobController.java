package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.AgentJobStartResponse;
import com.ohgiraffers.handlermethod.dto.AgentJobStatusResponse;
import com.ohgiraffers.handlermethod.dto.AgentRiskAnalyzeRequest;
import com.ohgiraffers.handlermethod.service.AgentJobService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent/jobs")
public class AgentJobController {

    private final AgentJobService agentJobService;

    public AgentJobController(AgentJobService agentJobService) {
        this.agentJobService = agentJobService;
    }

    @PostMapping
    public AgentJobStartResponse start(@RequestBody AgentRiskAnalyzeRequest request) {
        return agentJobService.start(request);
    }

    @GetMapping("/{jobId}")
    public AgentJobStatusResponse findById(@PathVariable Long jobId) {
        return agentJobService.findById(jobId);
    }

    @GetMapping
    public List<AgentJobStatusResponse> findRecent() {
        return agentJobService.findRecent();
    }
}
