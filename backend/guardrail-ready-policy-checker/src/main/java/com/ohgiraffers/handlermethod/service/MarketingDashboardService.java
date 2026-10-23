package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.MarketingFunnelResponse;
import com.ohgiraffers.handlermethod.dto.MarketingFunnelResponse.StageCount;
import com.ohgiraffers.handlermethod.legal.IntakeStatus;
import com.ohgiraffers.handlermethod.legal.IntakeEventType;
import com.ohgiraffers.handlermethod.repository.IntakeEventRepository;
import com.ohgiraffers.handlermethod.repository.AiSearchRequestRepository;
import com.ohgiraffers.handlermethod.repository.IntakeDistribution;
import com.ohgiraffers.handlermethod.repository.LegalIntakeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MarketingDashboardService {
    private final LegalIntakeRepository intakeRepository;
    private final AiSearchRequestRepository searchRequestRepository;
    private final IntakeEventRepository eventRepository;

    public MarketingDashboardService(LegalIntakeRepository intakeRepository, AiSearchRequestRepository searchRequestRepository,
                                      IntakeEventRepository eventRepository) {
        this.intakeRepository = intakeRepository;
        this.searchRequestRepository = searchRequestRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public MarketingFunnelResponse funnel() {
        long submitted = intakeRepository.count();
        long searchRequests = searchRequestRepository.count();
        long searchedIntakes = searchRequestRepository.countDistinctIntakes();
        long ready = intakeRepository.countByStatus(IntakeStatus.CONSULTATION_READY.name());
        Map<String, Long> eventCounts = new LinkedHashMap<>();
        for (IntakeEventType type : IntakeEventType.values()) {
            eventCounts.put(type.name(), eventRepository.countByEventType(type));
        }
        return new MarketingFunnelResponse(
                submitted, searchRequests, searchedIntakes, ready,
                List.of(
                        new StageCount("VISIT", eventCounts.get("VISIT"), "REPORTED_EVENT"),
                        new StageCount("INTAKE_STARTED", eventCounts.get("INTAKE_STARTED"), "REPORTED_EVENT"),
                        new StageCount("INTAKE_SUBMITTED", submitted, "STORED_INTAKE"),
                        new StageCount("SUMMARY_VIEWED", eventCounts.get("SUMMARY_VIEWED"), "REPORTED_EVENT"),
                        new StageCount("LAWYER_SEARCH_REQUESTED", searchedIntakes, "DISTINCT_INTAKE"),
                        new StageCount("CONSULTATION_READY", ready, "CURRENT_STATUS")
                ),
                distribution(intakeRepository.countByCaseType()),
                distribution(intakeRepository.countByDesiredNextStep()),
                distribution(intakeRepository.countByPreferredRegion()),
                "ALL_RECORDS_IN_CURRENT_DATABASE", false, eventCounts
        );
    }

    private Map<String, Long> distribution(List<IntakeDistribution> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        rows.forEach(row -> counts.put(row.getBucket(), row.getTotal()));
        return counts;
    }
}
