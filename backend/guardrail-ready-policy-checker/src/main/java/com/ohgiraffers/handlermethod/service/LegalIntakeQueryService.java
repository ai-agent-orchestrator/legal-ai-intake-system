package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.LegalIntakeHistoryResponse;
import com.ohgiraffers.handlermethod.dto.LegalIntakeSummaryResponse;
import com.ohgiraffers.handlermethod.entity.LegalIntakeHistory;
import com.ohgiraffers.handlermethod.repository.LegalIntakeHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LegalIntakeQueryService {

    private final LegalIntakeHistoryRepository legalIntakeHistoryRepository;

    public LegalIntakeQueryService(LegalIntakeHistoryRepository legalIntakeHistoryRepository) {
        this.legalIntakeHistoryRepository = legalIntakeHistoryRepository;
    }

    @Transactional(readOnly = true)
    public List<LegalIntakeHistoryResponse> findRecent(String readinessLevel, String decision) {
        List<LegalIntakeHistory> histories;

        if (readinessLevel != null && !readinessLevel.isBlank()) {
            histories = legalIntakeHistoryRepository.findByReadinessLevelOrderByCreatedAtDesc(readinessLevel);
        } else if (decision != null && !decision.isBlank()) {
            histories = legalIntakeHistoryRepository.findByDecisionOrderByCreatedAtDesc(decision);
        } else {
            histories = legalIntakeHistoryRepository.findTop50ByOrderByCreatedAtDesc();
        }

        return histories.stream()
                .map(LegalIntakeHistoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LegalIntakeSummaryResponse summarize() {
        List<LegalIntakeHistory> histories = legalIntakeHistoryRepository.findAll();

        long high = countByReadinessLevel(histories, "HIGH");
        long medium = countByReadinessLevel(histories, "MEDIUM");
        long low = countByReadinessLevel(histories, "LOW");

        Map<String, Long> decisionCounts = histories.stream()
                .collect(Collectors.groupingBy(LegalIntakeHistory::getDecision, Collectors.counting()));

        Map<String, Long> nextStepCounts = histories.stream()
                .collect(Collectors.groupingBy(LegalIntakeHistory::getRecommendedNextStep, Collectors.counting()));

        return new LegalIntakeSummaryResponse(
                histories.size(),
                high,
                medium,
                low,
                decisionCounts,
                nextStepCounts
        );
    }

    private long countByReadinessLevel(List<LegalIntakeHistory> histories, String readinessLevel) {
        return histories.stream()
                .filter(history -> readinessLevel.equals(history.getReadinessLevel()))
                .count();
    }
}
