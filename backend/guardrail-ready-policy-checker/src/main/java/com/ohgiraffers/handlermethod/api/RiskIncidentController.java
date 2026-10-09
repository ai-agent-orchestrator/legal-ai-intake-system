package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.RiskIncidentResponse;
import com.ohgiraffers.handlermethod.service.RiskIncidentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/incidents")
public class RiskIncidentController {

    private final RiskIncidentService riskIncidentService;

    public RiskIncidentController(RiskIncidentService riskIncidentService) {
        this.riskIncidentService = riskIncidentService;
    }

    @GetMapping
    public List<RiskIncidentResponse> findRecent() {
        return riskIncidentService.findRecent();
    }

    @PatchMapping("/{incidentId}/ack")
    public RiskIncidentResponse acknowledge(@PathVariable Long incidentId) {
        return riskIncidentService.acknowledge(incidentId);
    }

    @PatchMapping("/{incidentId}/resolve")
    public RiskIncidentResponse resolve(@PathVariable Long incidentId) {
        return riskIncidentService.resolve(incidentId);
    }
}
