package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.RiskIncidentResponse;
import com.ohgiraffers.handlermethod.entity.RiskIncident;
import com.ohgiraffers.handlermethod.incident.IncidentStatus;
import com.ohgiraffers.handlermethod.repository.RiskIncidentRepository;
import com.ohgiraffers.handlermethod.risk.RecommendedAction;
import com.ohgiraffers.handlermethod.support.ApiConflictException;
import com.ohgiraffers.handlermethod.support.IncidentNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

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
        return changeStatus(incidentId, IncidentStatus.ACKNOWLEDGED);
    }

    @Transactional
    public RiskIncidentResponse resolve(Long incidentId) {
        return changeStatus(incidentId, IncidentStatus.RESOLVED);
    }

    /*
     * 화면12: 같은 상태 재요청은 200(변경 없음), 종결 건을 확인됨으로 되돌리는 요청은 409.
     * 409 응답의 details.currentStatus로 화면이 버튼 상태를 바로 맞출 수 있다.
     */
    private RiskIncidentResponse changeStatus(Long incidentId, IncidentStatus next) {
        RiskIncident incident = riskIncidentRepository.findForUpdate(incidentId)
                .orElseThrow(IncidentNotFoundException::new);
        IncidentStatus current = incident.getStatus();
        if (!current.canChangeTo(next)) {
            throw new ApiConflictException(
                    current.isTerminal() ? "INCIDENT_ALREADY_RESOLVED" : "INCIDENT_STATUS_TRANSITION_NOT_ALLOWED",
                    "Cannot change incident status from " + current + " to " + next,
                    Map.of("incidentId", incidentId, "currentStatus", current.name(), "requestedStatus", next.name()));
        }
        incident.changeStatus(next);
        return RiskIncidentResponse.from(incident);
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

}
