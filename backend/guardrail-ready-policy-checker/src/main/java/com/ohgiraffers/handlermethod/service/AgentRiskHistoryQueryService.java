package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.AgentRiskHistoryResponse;
import com.ohgiraffers.handlermethod.dto.AgentRiskSummaryResponse;
import com.ohgiraffers.handlermethod.entity.AgentRiskHistory;
import com.ohgiraffers.handlermethod.repository.AgentRiskHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AgentRiskHistoryQueryService {

    private final AgentRiskHistoryRepository agentRiskHistoryRepository;

    public AgentRiskHistoryQueryService(AgentRiskHistoryRepository agentRiskHistoryRepository) {
        this.agentRiskHistoryRepository = agentRiskHistoryRepository;
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

        return histories.stream()
                .map(AgentRiskHistoryResponse::from)
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
