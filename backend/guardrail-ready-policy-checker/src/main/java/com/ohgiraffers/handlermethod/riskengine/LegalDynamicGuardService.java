package com.ohgiraffers.handlermethod.riskengine;

import com.ohgiraffers.handlermethod.support.AiSessionIds;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LegalDynamicGuardService {

    private static final int BLOCK_THRESHOLD = 100;
    private static final String DYNAMIC_BLOCK_RESPONSE = """
            같은 세션에서 위험 요청이 반복되어 현재 대화를 일시적으로 제한합니다.
            전자소송 절차 안내, 문서 정리, 증거 원본 보존처럼 안전한 범위의 질문으로 다시 시작해 주세요.
            """;

    private final Map<String, SessionRiskState> sessionStates = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    public LegalDynamicGuardService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        Gauge.builder("legal.ai.session.max.risk.score", this, LegalDynamicGuardService::maxRiskScore)
                .description("Maximum in-memory legal AI session risk score")
                .register(meterRegistry);
    }

    public DynamicGuardDecision preCheck(String sessionId) {
        SessionRiskState state = sessionStates.get(AiSessionIds.normalize(sessionId));
        if (state == null) {
            return DynamicGuardDecision.allow(0);
        }
        synchronized (state) {
            return state.blocked
                    ? DynamicGuardDecision.block("DYNAMIC_SESSION_BLOCKED", state.riskScore, DYNAMIC_BLOCK_RESPONSE)
                    : DynamicGuardDecision.allow(state.riskScore);
        }
    }

    public DynamicGuardDecision recordBlockedEvent(String sessionId, String reasonCode) {
        String normalizedSessionId = AiSessionIds.normalize(sessionId);
        String normalizedReason = normalizeReason(reasonCode);
        SessionRiskState state = sessionStates.computeIfAbsent(normalizedSessionId, ignored -> new SessionRiskState());

        synchronized (state) {
            state.riskScore += score(normalizedReason);
            state.totalBlockedCount++;
            state.reasonCounts.merge(normalizedReason, 1, Integer::sum);
            recordRiskEvent(normalizedReason);

            if (!state.blocked && shouldBlock(state, normalizedReason)) {
                state.blocked = true;
                recordDynamicBlock(normalizedReason);
            }
            return state.blocked
                    ? DynamicGuardDecision.block("DYNAMIC_SESSION_BLOCKED", state.riskScore, DYNAMIC_BLOCK_RESPONSE)
                    : DynamicGuardDecision.allow(state.riskScore);
        }
    }

    private boolean shouldBlock(SessionRiskState state, String reasonCode) {
        int sameReasonCount = state.reasonCounts.getOrDefault(reasonCode, 0);
        if (state.riskScore >= BLOCK_THRESHOLD) {
            return true;
        }
        if ("BLOCKED_LEGAL_HARM".equals(reasonCode) && sameReasonCount >= 2) {
            return true;
        }
        if ("BLOCKED_PROMPT_INJECTION".equals(reasonCode) && sameReasonCount >= 2) {
            return true;
        }
        if ("BLOCKED_JAILBREAK".equals(reasonCode) && sameReasonCount >= 2) {
            return true;
        }
        if ("BLOCKED_SECRET_REQUEST".equals(reasonCode) && sameReasonCount >= 2) {
            return true;
        }
        if ("OUT_OF_SCOPE_TOPIC".equals(reasonCode) && sameReasonCount >= 3) {
            return true;
        }
        if ("REQUEST_TOO_LONG".equals(reasonCode) && sameReasonCount >= 2) {
            return true;
        }
        return state.totalBlockedCount >= 3;
    }

    private int score(String reasonCode) {
        return switch (reasonCode) {
            case "BLOCKED_LEGAL_HARM" -> 70;
            case "BLOCKED_JAILBREAK", "BLOCKED_PROMPT_INJECTION" -> 60;
            case "BLOCKED_SECRET_REQUEST", "BLOCKED_PRIVACY_RISK", "BLOCKED_AUTH_BYPASS", "NEMO_INPUT_GUARDRAIL" -> 50;
            case "REQUEST_TOO_LONG", "COST_LIMIT_BLOCKED" -> 35;
            case "OUT_OF_SCOPE_TOPIC" -> 20;
            default -> 25;
        };
    }

    private void recordRiskEvent(String reasonCode) {
        Counter.builder("legal.ai.dynamic.risk.event")
                .tag("reason", reasonCode)
                .register(meterRegistry)
                .increment();
    }

    private void recordDynamicBlock(String triggerReason) {
        Counter.builder("legal.ai.dynamic.block")
                .tag("reason", "REPEATED_RISK")
                .tag("trigger", triggerReason)
                .register(meterRegistry)
                .increment();
    }

    private double maxRiskScore() {
        return sessionStates.values().stream()
                .mapToInt(state -> {
                    synchronized (state) {
                        return state.riskScore;
                    }
                })
                .max()
                .orElse(0);
    }

    private String normalizeReason(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return "UNKNOWN";
        }
        return reasonCode.trim();
    }

    private static final class SessionRiskState {
        private int riskScore;
        private int totalBlockedCount;
        private boolean blocked;
        private final Map<String, Integer> reasonCounts = new HashMap<>();
    }
}
