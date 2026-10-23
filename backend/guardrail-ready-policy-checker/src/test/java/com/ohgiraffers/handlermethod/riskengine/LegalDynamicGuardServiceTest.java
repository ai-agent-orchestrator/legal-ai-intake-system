package com.ohgiraffers.handlermethod.riskengine;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LegalDynamicGuardServiceTest {
    private SimpleMeterRegistry registry;
    private LegalDynamicGuardService guard;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        guard = new LegalDynamicGuardService(registry);
    }

    @Test
    void differentReasonsAccumulateAndBlockedSessionsStayBlocked() {
        assertThat(guard.recordBlockedEvent("risk", "BLOCKED_LEGAL_HARM").blocked()).isFalse();
        var result = guard.recordBlockedEvent("risk", "BLOCKED_PRIVACY_RISK");
        assertThat(result.blocked()).isTrue();
        assertThat(result.riskScore()).isEqualTo(120);
        assertThat(guard.preCheck("risk").reasonCode()).isEqualTo("DYNAMIC_SESSION_BLOCKED");
        assertThat(guard.preCheck("other").blocked()).isFalse();
    }

    @Test
    void repeatedLongRequestsBlockBelowTheScoreThreshold() {
        assertThat(guard.recordBlockedEvent("long", "REQUEST_TOO_LONG").blocked()).isFalse();
        var result = guard.recordBlockedEvent("long", "REQUEST_TOO_LONG");
        assertThat(result.blocked()).isTrue();
        assertThat(result.riskScore()).isEqualTo(70);
    }

    @Test
    void threeMixedBlockedRequestsBlockBelowTheScoreThreshold() {
        guard.recordBlockedEvent("mixed", "REQUEST_TOO_LONG");
        guard.recordBlockedEvent("mixed", "OUT_OF_SCOPE_TOPIC");
        var result = guard.recordBlockedEvent("mixed", "OUT_OF_SCOPE_TOPIC");
        assertThat(result.blocked()).isTrue();
        assertThat(result.riskScore()).isEqualTo(75);
    }

    @Test
    void missingIdsShareAnonymousAndWhitespaceDoesNotCreateAnotherSession() {
        guard.recordBlockedEvent(null, "BLOCKED_LEGAL_HARM");
        guard.recordBlockedEvent("  ", "BLOCKED_PRIVACY_RISK");
        assertThat(guard.preCheck("anonymous").riskScore()).isEqualTo(120);
        guard.recordBlockedEvent(" session ", "BLOCKED_LEGAL_HARM");
        guard.recordBlockedEvent("session", "BLOCKED_LEGAL_HARM");
        assertThat(guard.preCheck(" session ").riskScore()).isEqualTo(140);
        assertThat(guard.preCheck("session").blocked()).isTrue();
    }

    @Test
    void concurrentEventsAreNotLostAndTheBlockTransitionIsCountedOnce() throws Exception {
        int count = 200;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < count; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return guard.recordBlockedEvent("parallel", "OUT_OF_SCOPE_TOPIC");
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }
        assertThat(guard.preCheck("parallel").riskScore()).isEqualTo(count * 20);
        assertThat(guard.preCheck("parallel").blocked()).isTrue();
        assertThat(registry.get("legal.ai.dynamic.risk.event").counter().count()).isEqualTo(count);
        assertThat(registry.get("legal.ai.dynamic.block").counter().count()).isEqualTo(1);
        assertThat(registry.get("legal.ai.session.max.risk.score").gauge().value()).isEqualTo(count * 20);
    }
}
