package com.ohgiraffers.handlermethod.cost;

import com.ohgiraffers.handlermethod.guardrail.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GuardrailCostTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AiCostControlService cost = new AiCostControlService(new BigDecimal("10"), new BigDecimal("5"),
            new BigDecimal("1"), new BigDecimal("0.15"), new BigDecimal("0.60"), 256, 1, 10, registry);
    private final InputGuardrailClient input = mock(InputGuardrailClient.class);
    private final OutputGuardrailClient output = mock(OutputGuardrailClient.class);
    private final CostedGuardrailService service = new CostedGuardrailService(input, output, cost, "guard-model",
            new BigDecimal("0.10"), new BigDecimal("0.20"), 100, 20);

    @Test
    void blockedPolicyWithUnknownUsageIsChargedAndSharesTheAnonymousBudget() {
        when(input.check("민사")).thenReturn(new InputGuardrailResult("BLOCKED", "차단", "INPUT"));
        var checked = service.checkInput("민사", null);
        assertThat(checked.result().allowed()).isFalse();
        assertThat(checked.costSource()).isEqualTo("ESTIMATED");
        assertThat(registry.get("ai.cost.estimated.usage.usd").gauge().value()).isEqualTo(0.0146);
        assertThat(registry.get("ai.cost.reserved.usd").gauge().value()).isZero();
    }

    @Test
    void reportedUsageUsesTheGuardrailRatesRatherThanAnswerRates() {
        when(input.check("민사")).thenReturn(new InputGuardrailResult("PASSED", "민사", "INPUT",
                new GuardrailUsage(10, 5)));
        assertThat(service.checkInput("민사", "s").costSource()).isEqualTo("REPORTED");
        assertThat(registry.get("ai.cost.actual.usd").gauge().value()).isEqualTo(0.002);
    }

    @Test
    void failedRemoteCheckKeepsAnEstimateInsteadOfPretendingItWasFree() {
        when(output.check("답변")).thenThrow(new IllegalStateException("timeout"));
        assertThatThrownBy(() -> service.checkOutput("답변", "s")).isInstanceOf(IllegalStateException.class);
        assertThat(registry.get("ai.cost.estimated.usage.usd").gauge().value()).isPositive();
        assertThat(registry.get("ai.cost.reserved.usd").gauge().value()).isZero();
    }

    @Test
    void localOutputRejectionDoesNotNeedBudgetOrCallThePaidClient() {
        var held = cost.reserveStageWithBlock(AiCostControlService.CostStage.ANSWER, 0, 5000,
                "test", "s", BigDecimal.ZERO, new BigDecimal("0.20")).reservation();
        var checked = service.checkOutput("가".repeat(12001), "s");
        assertThat(checked.result().rail()).isEqualTo("local output policy");
        assertThat(checked.usageAvailable()).isTrue();
        assertThat(checked.result().usage().prompt_tokens().intValue()).isZero();
        verifyNoInteractions(output);
        cost.release(held, "CLEANUP");
    }

    @Test
    void duplicateCallbacksCannotDoubleChargeOrReleaseAnotherCallsReservation() {
        var first = cost.reserveWithBlock("", "민사", "test", "s").reservation();
        var second = cost.reserveWithBlock("", "민사", "test", "s").reservation();
        cost.recordUsage(first, 10, 4, true);
        cost.recordUsage(first, 1000, 1000, true);
        cost.release(first, "LATE_CALLBACK");
        assertThat(registry.get("ai.cost.actual.usd").gauge().value()).isEqualTo(0.0039);
        assertThat(registry.get("ai.cost.reserved.usd").gauge().value()).isEqualTo(second.estimatedUsd().doubleValue());
        cost.release(second, "CLEANUP");
        cost.recordUsage(second, 1000, 1000, true);
        assertThat(registry.get("ai.cost.actual.usd").gauge().value()).isEqualTo(0.0039);
    }

    @ParameterizedTest
    @ValueSource(strings = {"input", "output"})
    void reportedZeroUsageIsPreserved(String kind) {
        when(input.check("민사")).thenReturn(new InputGuardrailResult("PASSED", "민사", "INPUT", new GuardrailUsage(0, 0)));
        when(output.check("민사")).thenReturn(new OutputGuardrailResult("PASSED", "민사", "OUTPUT", new GuardrailUsage(0, 0)));
        var checked = kind.equals("input") ? service.checkInput("민사", "s") : service.checkOutput("민사", "s");
        assertThat(checked.usageAvailable()).isTrue();
        assertThat(checked.costSource()).isEqualTo("REPORTED");
        assertThat(registry.get("ai.cost.estimated.usage.usd").gauge().value()).isZero();
    }
}
