package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.LegalIntakeAnalyzeRequest;
import com.ohgiraffers.handlermethod.dto.LegalIntakeAnalyzeResponse;
import com.ohgiraffers.handlermethod.entity.LegalIntakeHistory;
import com.ohgiraffers.handlermethod.legal.LegalIntakeDecision;
import com.ohgiraffers.handlermethod.legal.LegalIntakeSignal;
import com.ohgiraffers.handlermethod.legal.LegalNextStep;
import com.ohgiraffers.handlermethod.legal.LegalReadinessLevel;
import com.ohgiraffers.handlermethod.repository.LegalIntakeHistoryRepository;
import com.ohgiraffers.handlermethod.support.TraceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class LegalIntakeDecisionService {

    private final LegalIntakeHistoryRepository legalIntakeHistoryRepository;

    public LegalIntakeDecisionService(LegalIntakeHistoryRepository legalIntakeHistoryRepository) {
        this.legalIntakeHistoryRepository = legalIntakeHistoryRepository;
    }

    @Transactional
    public LegalIntakeAnalyzeResponse analyze(LegalIntakeAnalyzeRequest request) {
        LegalIntakeDraft draft = toDraft(request);
        List<LegalIntakeSignal> signals = collectSignals(draft);
        int score = calculateScore(signals);
        LegalReadinessLevel level = decideLevel(score);
        LegalIntakeDecision decision = decide(signals, level);
        LegalNextStep nextStep = decideNextStep(signals, decision);
        String traceId = TraceContext.currentTraceId();

        LegalIntakeHistory history = legalIntakeHistoryRepository.save(
                LegalIntakeHistory.create(
                        draft.caseType(),
                        draft.summary(),
                        draft.claimPurpose(),
                        draft.hasEvidence(),
                        draft.hasDeadline(),
                        draft.opponentKnown(),
                        draft.damageAmountKnown(),
                        draft.currentStage(),
                        draft.urgent(),
                        decision.name(),
                        score,
                        level.name(),
                        joinSignals(signals),
                        nextStep.name(),
                        traceId
                )
        );

        return new LegalIntakeAnalyzeResponse(
                decision.name(),
                score,
                level.name(),
                signals.stream().map(Enum::name).toList(),
                nextStep.name(),
                true,
                history.getId(),
                traceId
        );
    }

    private LegalIntakeDraft toDraft(LegalIntakeAnalyzeRequest request) {
        if (request == null) {
            return new LegalIntakeDraft(
                    "unknown",
                    "empty legal intake",
                    "unknown",
                    false,
                    false,
                    false,
                    false,
                    "unknown",
                    false
            );
        }

        return new LegalIntakeDraft(
                valueOrDefault(request.caseType(), "unknown"),
                valueOrDefault(request.summary(), "empty legal intake"),
                valueOrDefault(request.claimPurpose(), "unknown"),
                bool(request.hasEvidence()),
                bool(request.hasDeadline()),
                bool(request.opponentKnown()),
                bool(request.damageAmountKnown()),
                valueOrDefault(request.currentStage(), "unknown"),
                bool(request.urgent())
        );
    }

    private List<LegalIntakeSignal> collectSignals(LegalIntakeDraft draft) {
        List<LegalIntakeSignal> signals = new ArrayList<>();

        if (!"unknown".equals(draft.caseType())) {
            signals.add(LegalIntakeSignal.CASE_TYPE_PROVIDED);
        }
        if (!"unknown".equals(draft.claimPurpose())) {
            signals.add(LegalIntakeSignal.CLAIM_PROVIDED);
        }
        if (draft.hasEvidence()) {
            signals.add(LegalIntakeSignal.EVIDENCE_EXISTS);
        }
        if (draft.hasDeadline()) {
            signals.add(LegalIntakeSignal.DEADLINE_EXISTS);
        }
        if (draft.opponentKnown()) {
            signals.add(LegalIntakeSignal.OPPONENT_KNOWN);
        }
        if (draft.damageAmountKnown()) {
            signals.add(LegalIntakeSignal.DAMAGE_AMOUNT_KNOWN);
        }
        if (!"unknown".equals(draft.currentStage())) {
            signals.add(LegalIntakeSignal.STAGE_PROVIDED);
        }
        if (draft.urgent()) {
            signals.add(LegalIntakeSignal.URGENT);
        }

        return signals;
    }

    private int calculateScore(List<LegalIntakeSignal> signals) {
        int score = 0;

        if (signals.contains(LegalIntakeSignal.CASE_TYPE_PROVIDED)) {
            score += 10;
        }
        if (signals.contains(LegalIntakeSignal.CLAIM_PROVIDED)) {
            score += 15;
        }
        if (signals.contains(LegalIntakeSignal.EVIDENCE_EXISTS)) {
            score += 20;
        }
        if (signals.contains(LegalIntakeSignal.DEADLINE_EXISTS)) {
            score += 15;
        }
        if (signals.contains(LegalIntakeSignal.OPPONENT_KNOWN)) {
            score += 10;
        }
        if (signals.contains(LegalIntakeSignal.DAMAGE_AMOUNT_KNOWN)) {
            score += 15;
        }
        if (signals.contains(LegalIntakeSignal.STAGE_PROVIDED)) {
            score += 10;
        }
        if (signals.contains(LegalIntakeSignal.URGENT)) {
            score += 5;
        }

        return Math.min(score, 100);
    }

    private LegalReadinessLevel decideLevel(int score) {
        if (score >= 80) {
            return LegalReadinessLevel.HIGH;
        }
        if (score >= 50) {
            return LegalReadinessLevel.MEDIUM;
        }
        return LegalReadinessLevel.LOW;
    }

    private LegalIntakeDecision decide(List<LegalIntakeSignal> signals, LegalReadinessLevel level) {
        if (signals.contains(LegalIntakeSignal.URGENT) && signals.contains(LegalIntakeSignal.DEADLINE_EXISTS)) {
            return LegalIntakeDecision.URGENT_REVIEW_REQUIRED;
        }
        if (signals.contains(LegalIntakeSignal.DEADLINE_EXISTS) && !signals.contains(LegalIntakeSignal.EVIDENCE_EXISTS)) {
            return LegalIntakeDecision.DEADLINE_FOCUSED_REVIEW;
        }
        if (!signals.contains(LegalIntakeSignal.DAMAGE_AMOUNT_KNOWN)
                && signals.contains(LegalIntakeSignal.EVIDENCE_EXISTS)
                && level != LegalReadinessLevel.LOW) {
            return LegalIntakeDecision.READY_WITH_MISSING_AMOUNT;
        }
        if (level == LegalReadinessLevel.HIGH) {
            return LegalIntakeDecision.CONSULTATION_READY;
        }
        if (level == LegalReadinessLevel.MEDIUM) {
            return LegalIntakeDecision.BASIC_REVIEW_READY;
        }
        return LegalIntakeDecision.INCOMPLETE_INTAKE;
    }

    private LegalNextStep decideNextStep(List<LegalIntakeSignal> signals, LegalIntakeDecision decision) {
        if (decision == LegalIntakeDecision.URGENT_REVIEW_REQUIRED) {
            return LegalNextStep.ESCALATE_URGENT_REVIEW;
        }
        if (signals.contains(LegalIntakeSignal.DEADLINE_EXISTS) && !signals.contains(LegalIntakeSignal.EVIDENCE_EXISTS)) {
            return LegalNextStep.CHECK_DEADLINE_AND_LIMITATION_PERIOD;
        }
        if (!signals.contains(LegalIntakeSignal.EVIDENCE_EXISTS)) {
            return LegalNextStep.ORGANIZE_EVIDENCE_TIMELINE;
        }
        if (!signals.contains(LegalIntakeSignal.DAMAGE_AMOUNT_KNOWN)) {
            return LegalNextStep.ORGANIZE_CLAIM_AMOUNT_AND_EVIDENCE_TIMELINE;
        }
        if (decision == LegalIntakeDecision.CONSULTATION_READY) {
            return LegalNextStep.PREPARE_LAWYER_CONSULTATION_PACKET;
        }
        return LegalNextStep.COLLECT_BASIC_FACTS;
    }

    private String joinSignals(List<LegalIntakeSignal> signals) {
        return String.join(",", signals.stream().map(Enum::name).toList());
    }

    private boolean bool(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private String valueOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private record LegalIntakeDraft(
            String caseType,
            String summary,
            String claimPurpose,
            boolean hasEvidence,
            boolean hasDeadline,
            boolean opponentKnown,
            boolean damageAmountKnown,
            String currentStage,
            boolean urgent
    ) {
    }
}
