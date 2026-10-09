package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.RiskIncidentResponse;
import com.ohgiraffers.handlermethod.entity.RiskIncident;
import com.ohgiraffers.handlermethod.incident.IncidentStatus;
import com.ohgiraffers.handlermethod.repository.RiskIncidentRepository;
import com.ohgiraffers.handlermethod.risk.RecommendedAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RiskIncidentService {

    private final RiskIncidentRepository riskIncidentRepository;

    public RiskIncidentService(RiskIncidentRepository riskIncidentRepository) {
        this.riskIncidentRepository = riskIncidentRepository;
    }

    @Transactional
    public void createIfNeeded(String decision,
                               int riskScore,
                               String riskLevel,
                               String recommendedAction,
                               Long riskHistoryId,
                               String traceId) {
        if (!shouldCreateIncident(riskScore, recommendedAction)) {
            return;
        }

        RiskIncident incident = RiskIncident.open(
                "AI_AGENT_RISK",
                riskLevel,
                decision,
                riskScore,
                recommendedAction,
                riskHistoryId,
                traceId,
                "High-risk AI agent behavior requires operator review."
        );
        riskIncidentRepository.save(incident);
    }

    @Transactional(readOnly = true)
    public List<RiskIncidentResponse> findRecent() {
        return riskIncidentRepository.findTop50ByOrderByCreatedAtDesc()
                .stream()
                .map(RiskIncidentResponse::from)
                .toList();
    }

    @Transactional
    public RiskIncidentResponse acknowledge(Long incidentId) {
        RiskIncident incident = findIncident(incidentId);
        incident.acknowledge();
        return RiskIncidentResponse.from(riskIncidentRepository.save(incident));
    }

    @Transactional
    public RiskIncidentResponse resolve(Long incidentId) {
        RiskIncident incident = findIncident(incidentId);
        incident.resolve();
        return RiskIncidentResponse.from(riskIncidentRepository.save(incident));
    }

    @Transactional(readOnly = true)
    public long countOpen() {
        return riskIncidentRepository.countByStatus(IncidentStatus.OPEN);
    }

    private boolean shouldCreateIncident(int riskScore, String recommendedAction) {
        return riskScore >= 80
                || RecommendedAction.BLOCK_AND_ESCALATE.name().equals(recommendedAction)
                || RecommendedAction.CREATE_INCIDENT.name().equals(recommendedAction);
    }

    private RiskIncident findIncident(Long incidentId) {
        return riskIncidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Risk incident not found: " + incidentId));
    }
}
