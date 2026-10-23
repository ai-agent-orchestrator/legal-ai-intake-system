package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.AgentRiskHistoryResponse;
import com.ohgiraffers.handlermethod.dto.AgentRiskSummaryResponse;
import com.ohgiraffers.handlermethod.entity.AgentRiskHistory;
import com.ohgiraffers.handlermethod.repository.AgentRiskHistoryRepository;
import com.ohgiraffers.handlermethod.repository.AgentRunHistoryRepository;
import com.ohgiraffers.handlermethod.risk.AgentRiskScoring;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AgentRiskHistoryQueryService {

    private final AgentRiskHistoryRepository agentRiskHistoryRepository;
    private final AgentRunHistoryRepository agentRunHistoryRepository;

    public AgentRiskHistoryQueryService(AgentRiskHistoryRepository agentRiskHistoryRepository,
                                       AgentRunHistoryRepository agentRunHistoryRepository) {
        this.agentRiskHistoryRepository = agentRiskHistoryRepository;
        this.agentRunHistoryRepository = agentRunHistoryRepository;
    }

    @Transactional(readOnly = true)
    public List<AgentRiskHistoryResponse> findRecent(String riskLevel, String decision) {
        List<AgentRiskHistory> histories;

        if (riskLevel != null && !riskLevel.isBlank()) {
            histories = agentRiskHistoryRepository.findByRiskLevelOrderByCreatedAtDesc(riskLevel);
        } else if (decision != null && !decision.isBlank()) {
            histories = agentRiskHistoryRepository.findByDecisionOrderByCreatedAtDesc(decision);
        } else {
            histories = agentRiskHistoryRepository.findTop50ByOrderByCreatedAtDesc();
        }

        var traceIds = histories.stream().map(AgentRiskHistory::getTraceId).filter(Objects::nonNull).distinct().toList();
        var runs = traceIds.isEmpty() ? List.<com.ohgiraffers.handlermethod.entity.AgentRunHistory>of()
                : agentRunHistoryRepository.findByTraceIdIn(traceIds);
        return histories.stream()
                .map(history -> {
                    // Trace IDs can be reused by clients; only an unambiguous matching score is evidence.
                    var candidates = runs.stream().filter(run -> Objects.equals(run.getTraceId(), history.getTraceId())
                            && Objects.equals(run.getUserInput(), history.getUserInput())
                            && Objects.equals(run.getToolName(), history.getToolName())
                            && Objects.equals(run.getDecision(), history.getDecision())
                            && run.getRiskScore() == history.getRiskScore()).toList();
                    var retryPoints = candidates.stream().map(run -> AgentRiskScoring.retryPoints(run.getRetryCount())).distinct().toList();
                    Integer retries = !candidates.isEmpty() && retryPoints.size() == 1 ? candidates.getFirst().getRetryCount() : null;
                    return AgentRiskHistoryResponse.from(history, retries);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public AgentRiskSummaryResponse summarize() {
        List<AgentRiskHistory> histories = agentRiskHistoryRepository.findAll();

        long high = countByRiskLevel(histories, "HIGH");
        long medium = countByRiskLevel(histories, "MEDIUM");
        long low = countByRiskLevel(histories, "LOW");

        Map<String, Long> decisionCounts = histories.stream()
                .collect(Collectors.groupingBy(AgentRiskHistory::getDecision, Collectors.counting()));

        Map<String, Long> recommendedActionCounts = histories.stream()
                .collect(Collectors.groupingBy(AgentRiskHistory::getRecommendedAction, Collectors.counting()));

        return new AgentRiskSummaryResponse(
                histories.size(),
                high,
                medium,
                low,
                decisionCounts,
                recommendedActionCounts
        );
    }

    private long countByRiskLevel(List<AgentRiskHistory> histories, String riskLevel) {
        return histories.stream()
                .filter(history -> riskLevel.equals(history.getRiskLevel()))
                .count();
    }
}
