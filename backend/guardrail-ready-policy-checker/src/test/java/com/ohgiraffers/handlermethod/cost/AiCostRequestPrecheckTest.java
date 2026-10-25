package com.ohgiraffers.handlermethod.cost;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiCostRequestPrecheckTest {

    /** 100 input tokens at $1 per 1K = $0.10 per stage. */
    private static AiCostControlService.StageEstimate tenCents(AiCostControlService.CostStage stage) {
        return new AiCostControlService.StageEstimate(stage, 100, 0, BigDecimal.ONE, BigDecimal.ONE);
    }

    private static final List<AiCostControlService.StageEstimate> TWENTY_CENTS = List.of(
            tenCents(AiCostControlService.CostStage.INPUT_GUARDRAIL), tenCents(AiCostControlService.CostStage.ANSWER));
    private static final List<AiCostControlService.StageEstimate> FORTY_CENTS = List.of(
            tenCents(AiCostControlService.CostStage.INPUT_GUARDRAIL), tenCents(AiCostControlService.CostStage.ANSWER),
            tenCents(AiCostControlService.CostStage.OUTPUT_GUARDRAIL), tenCents(AiCostControlService.CostStage.OUTPUT_GUARDRAIL));

    private AiCostControlService costControl(String monthly, String daily, String session) {
        return new AiCostControlService(new BigDecimal(monthly), new BigDecimal(daily), new BigDecimal(session),
                new BigDecimal("0.15"), new BigDecimal("0.60"), 256, 1, 10, new SimpleMeterRegistry());
    }

    @Test
    void aPlanThatFitsPassesAndReservesNothing() {
        var cost = costControl("2", "1", "0.3");
        BigDecimal before = cost.remainingBudgetUsd();
        assertThat(cost.precheckRequestBudget(TWENTY_CENTS, "test", "s")).isEmpty();
        assertThat(cost.precheckRequestBudget(TWENTY_CENTS, "test", "s")).isEmpty();
        assertThat(cost.remainingBudgetUsd()).isEqualByComparingTo(before);
    }

    @Test
    void theSumOfAllStagesIsCheckedNotEachStageAlone() {
        // Every single stage (0.10) fits the 0.3 session budget, but the whole request (0.40) does not.
        var cost = costControl("2", "1", "0.3");
        var block = cost.precheckRequestBudget(FORTY_CENTS, "test", "s").orElseThrow();
        assertThat(block.reasonCode()).isEqualTo("COST_LIMIT_BLOCKED");
        assertThat(block.detailCode()).isEqualTo("SESSION_BUDGET_EXCEEDED");
    }

    @Test
    void alreadyReservedSessionSpendCountsTowardsThePlan() {
        var cost = costControl("2", "1", "0.3");
        var held = cost.reserveStageWithBlock(AiCostControlService.CostStage.ANSWER, 150, 0, "test", " s ",
                BigDecimal.ONE, BigDecimal.ONE);
        assertThat(held.allowed()).isTrue();
        assertThat(cost.precheckRequestBudget(TWENTY_CENTS, "test", "s").orElseThrow().detailCode())
                .isEqualTo("SESSION_BUDGET_EXCEEDED");
        assertThat(cost.precheckRequestBudget(TWENTY_CENTS, "test", "other")).isEmpty();
        cost.release(held.reservation(), "TEST_CLEANUP");
        assertThat(cost.precheckRequestBudget(TWENTY_CENTS, "test", "s")).isEmpty();
    }

    @Test
    void dailyAndMonthlyLimitsUseTheSamePrecedenceAsStageReservations() {
        assertThat(costControl("2", "0.3", "1").precheckRequestBudget(FORTY_CENTS, "test", "s")
                .orElseThrow().detailCode()).isEqualTo("DAILY_BUDGET_EXCEEDED");
        assertThat(costControl("0.3", "0.3", "0.3").precheckRequestBudget(FORTY_CENTS, "test", "s")
                .orElseThrow().detailCode()).isEqualTo("MONTHLY_BUDGET_EXCEEDED");
    }

    @Test
    void negativeEstimatesAreRejected() {
        var cost = costControl("2", "1", "0.3");
        var negative = new AiCostControlService.StageEstimate(AiCostControlService.CostStage.ANSWER, -1, 0,
                BigDecimal.ONE, BigDecimal.ONE);
        assertThatThrownBy(() -> cost.precheckRequestBudget(List.of(negative), "test", "s"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
