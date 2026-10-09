package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.AgentPracticeRequest;
import com.ohgiraffers.handlermethod.dto.AgentPracticeResponse;
import com.ohgiraffers.handlermethod.service.AgentPracticeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/practice")
public class AgentPracticeController {

    private final AgentPracticeService agentPracticeService;

    public AgentPracticeController(AgentPracticeService agentPracticeService) {
        this.agentPracticeService = agentPracticeService;
    }

    @PostMapping("/run")
    public AgentPracticeResponse run(@RequestBody(required = false) AgentPracticeRequest request) {
        return agentPracticeService.run(request);
    }

    @PostMapping("/tool-error")
    public AgentPracticeResponse toolError(@RequestBody(required = false) AgentPracticeRequest request) {
        return agentPracticeService.toolError(request);
    }

    @PostMapping("/retry")
    public AgentPracticeResponse retry(@RequestBody(required = false) AgentPracticeRequest request) {
        return agentPracticeService.retry(request);
    }

    @PostMapping("/approval")
    public AgentPracticeResponse approval(@RequestBody(required = false) AgentPracticeRequest request) {
        return agentPracticeService.approval(request);
    }

    @PostMapping("/policy-violation")
    public AgentPracticeResponse policyViolation(@RequestBody(required = false) AgentPracticeRequest request) {
        return agentPracticeService.policyViolation(request);
    }

    @PostMapping("/external-api")
    public AgentPracticeResponse externalApi(@RequestBody(required = false) AgentPracticeRequest request) {
        return agentPracticeService.externalApi(request);
    }

    @PostMapping("/db-write")
    public AgentPracticeResponse dbWrite(@RequestBody(required = false) AgentPracticeRequest request) {
        return agentPracticeService.dbWrite(request);
    }
}
