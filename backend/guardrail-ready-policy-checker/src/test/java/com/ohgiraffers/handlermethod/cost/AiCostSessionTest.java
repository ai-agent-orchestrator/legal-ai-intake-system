package com.ohgiraffers.handlermethod.cost;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AiCostSessionTest {

    @Test
    void whitespaceCannotBypassTheSessionBudget() {
        var cost = costControl();
        var first = cost.reserveWithBlock("", "민사", "test", " session ");
        assertThat(first.allowed()).isTrue();
        assertThat(first.reservation().sessionId()).isEqualTo("session");
        var second = cost.reserveWithBlock("", "민사", "test", "session");
        assertThat(second.allowed()).isFalse();
        assertThat(second.block().detailCode()).isEqualTo("SESSION_BUDGET_EXCEEDED");
        assertThat(cost.reserveWithBlock("", "민사", "test", "other").allowed()).isTrue();
    }

    @Test
    void missingIdsAndTheLegacyReserveMethodUseTheSameAnonymousBudget() {
        var cost = costControl();
        var reservation = cost.reserve("", "민사", "test").orElseThrow();
        assertThat(reservation.sessionId()).isEqualTo("anonymous");
        assertThat(cost.reserveWithBlock("", "민사", "test", null).allowed()).isFalse();
        assertThat(cost.reserveWithBlock("", "민사", "test", "  ").allowed()).isFalse();
        cost.release(reservation, "TEST_CLEANUP");
        assertThat(cost.reserveWithBlock("", "민사", "test", " anonymous ").allowed()).isTrue();
    }

    private AiCostControlService costControl() {
        return new AiCostControlService(new BigDecimal("2"), new BigDecimal("1"),
                new BigDecimal("0.3"), new BigDecimal("0.15"), new BigDecimal("0.60"),
                256, 1, 10, new SimpleMeterRegistry());
    }
}
