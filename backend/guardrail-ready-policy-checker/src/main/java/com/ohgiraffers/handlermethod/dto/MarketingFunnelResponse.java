package com.ohgiraffers.handlermethod.dto;

import java.util.List;
import java.util.Map;

public record MarketingFunnelResponse(
        long submittedIntakeCount,
        long searchRequestCount,
        long searchRequestedIntakeCount,
        long consultationReadyIntakeCount,
        List<StageCount> stages,
        Map<String, Long> caseTypeDistribution,
        Map<String, Long> desiredNextStepDistribution,
        Map<String, Long> regionDistribution,
        String measurementBasis,
        boolean dropOffMeasured,
        Map<String, Long> eventCounts
) {
    public record StageCount(String stage, Long count, String measurement) {
    }
}
