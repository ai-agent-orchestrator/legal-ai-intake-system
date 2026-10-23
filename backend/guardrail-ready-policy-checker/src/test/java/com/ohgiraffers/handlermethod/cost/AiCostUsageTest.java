package com.ohgiraffers.handlermethod.cost;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AiCostUsageTest {

    @ParameterizedTest
    @CsvSource({"0.2,1,1,MONTHLY_BUDGET_EXCEEDED", "2,0.2,1,DAILY_BUDGET_EXCEEDED",
            "2,1,0.3,SESSION_BUDGET_EXCEEDED"})
    void missingUsageIsSettledAsAnEstimateAndCountsAgainstEveryBudget(
            String monthly, String daily, String session, String expectedReason) {
        var registry = new SimpleMeterRegistry();
        var cost = costControl(registry, monthly, daily, session);
        var reservation = cost.reserveWithBlock("", "민사", "test", "session").reservation();

        assertThat(cost.recordUsage(reservation, 0, 0, false)).isEqualTo(AiCostControlService.CostSource.ESTIMATED);
        assertThat(registry.get("ai.cost.reserved.usd").gauge().value()).isZero();
        assertThat(registry.get("ai.cost.actual.usd").gauge().value()).isZero();
        assertThat(registry.get("ai.cost.estimated.usage.usd").gauge().value())
                .isEqualTo(reservation.estimatedUsd().doubleValue());
        assertThat(registry.get("ai.cost.daily.estimated.usage.usd").gauge().value())
                .isEqualTo(reservation.estimatedUsd().doubleValue());
        assertThat(registry.get("ai.cost.accounted.usd").gauge().value())
                .isEqualTo(reservation.estimatedUsd().doubleValue());
        assertThat(registry.get("ai.cost.usage.missing").counter().count()).isEqualTo(1);
        assertThat(cost.remainingBudgetUsd()).isEqualByComparingTo(
                new BigDecimal(monthly).subtract(reservation.estimatedUsd()));

        var next = cost.reserveWithBlock("", "민사", "test", "session");
        assertThat(next.allowed()).isFalse();
        assertThat(next.block().detailCode()).isEqualTo(expectedReason);
    }

    @Test
    void reportedZeroUsageIsNotTreatedAsMissing() {
        var registry = new SimpleMeterRegistry();
        var cost = costControl(registry, "2", "1", "0.3");
        var reservation = cost.reserveWithBlock("", "민사", "test", "session").reservation();
        assertThat(cost.recordUsage(reservation, 0, 0, true)).isEqualTo(AiCostControlService.CostSource.REPORTED);
        assertThat(registry.get("ai.cost.actual.usd").gauge().value()).isZero();
        assertThat(registry.get("ai.cost.estimated.usage.usd").gauge().value()).isZero();
        assertThat(registry.find("ai.cost.usage.missing").counter()).isNull();
        assertThat(cost.reserveWithBlock("", "민사", "test", "session").allowed()).isTrue();
    }

    @Test
    void reportedTokensReplaceTheReservationWithReportedCost() {
        var registry = new SimpleMeterRegistry();
        var cost = costControl(registry, "2", "1", "0.3");
        var reservation = cost.reserveWithBlock("", "민사", "test", "session").reservation();
        assertThat(cost.recordUsage(reservation, 10, 4, true)).isEqualTo(AiCostControlService.CostSource.REPORTED);
        assertThat(registry.get("ai.cost.reserved.usd").gauge().value()).isZero();
        assertThat(registry.get("ai.cost.actual.usd").gauge().value()).isEqualTo(0.0039);
        assertThat(registry.get("ai.cost.estimated.usage.usd").gauge().value()).isZero();
        assertThat(cost.remainingBudgetUsd()).isEqualByComparingTo("1.9961");
    }

    private AiCostControlService costControl(SimpleMeterRegistry registry, String monthly, String daily, String session) {
        return new AiCostControlService(new BigDecimal(monthly), new BigDecimal(daily), new BigDecimal(session),
                new BigDecimal("0.15"), new BigDecimal("0.60"), 256, 1, 10, registry);
    }
}
