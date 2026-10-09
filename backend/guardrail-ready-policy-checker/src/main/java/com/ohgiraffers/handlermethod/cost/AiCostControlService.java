package com.ohgiraffers.handlermethod.cost;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
public class AiCostControlService {

    private static final BigDecimal ONE_THOUSAND = BigDecimal.valueOf(1_000);
    private static final int CHARS_PER_ESTIMATED_TOKEN = 4;

    private final BigDecimal monthlyBudgetUsd;
    private final BigDecimal inputUsdPer1kTokens;
    private final BigDecimal outputUsdPer1kTokens;
    private final int maxOutputTokens;
    private final MeterRegistry meterRegistry;

    private BigDecimal reservedUsd = BigDecimal.ZERO;
    private BigDecimal actualUsd = BigDecimal.ZERO;

    public AiCostControlService(
            @Value("${llm.cost.monthly-budget-usd}") BigDecimal monthlyBudgetUsd,
            @Value("${llm.cost.input-usd-per-1k-tokens}") BigDecimal inputUsdPer1kTokens,
            @Value("${llm.cost.output-usd-per-1k-tokens}") BigDecimal outputUsdPer1kTokens,
            @Value("${llm.max-output-tokens}") int maxOutputTokens,
            MeterRegistry meterRegistry
    ) {
        this.monthlyBudgetUsd = monthlyBudgetUsd;
        this.inputUsdPer1kTokens = inputUsdPer1kTokens;
        this.outputUsdPer1kTokens = outputUsdPer1kTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.meterRegistry = meterRegistry;

        Gauge.builder("ai.cost.budget.usd", this, control -> control.monthlyBudgetUsd.doubleValue())
                .description("Configured application-side AI budget")
                .register(meterRegistry);
        Gauge.builder("ai.cost.reserved.usd", this, AiCostControlService::reservedAmount)
                .description("Estimated cost reserved by in-flight AI calls")
                .register(meterRegistry);
        Gauge.builder("ai.cost.actual.usd", this, AiCostControlService::actualAmount)
                .description("Actual recorded AI cost for the current application period")
                .register(meterRegistry);
    }

    public synchronized Optional<CostReservation> reserve(String systemPrompt,
                                                           String userMessage,
                                                           String model) {
        int estimatedInputTokens = estimateTokens(systemPrompt) + estimateTokens(userMessage);
        BigDecimal estimatedUsd = calculateCost(
                estimatedInputTokens,
                maxOutputTokens
        );

        if (actualUsd.add(reservedUsd).add(estimatedUsd).compareTo(monthlyBudgetUsd) > 0) {
            Counter.builder("ai.cost.blocked")
                    .tag("reason", "BUDGET_EXCEEDED")
                    .tag("model", normalize(model))
                    .register(meterRegistry)
                    .increment();
            return Optional.empty();
        }

        reservedUsd = reservedUsd.add(estimatedUsd);
        Counter.builder("ai.cost.estimated")
                .tag("model", normalize(model))
                .register(meterRegistry)
                .increment(estimatedUsd.doubleValue());

        return Optional.of(new CostReservation(model, estimatedUsd));
    }

    public synchronized void recordActualUsage(CostReservation reservation,
                                                int promptTokens,
                                                int completionTokens) {
        BigDecimal actualCost = calculateCost(promptTokens, completionTokens);
        reservedUsd = reservedUsd.subtract(reservation.estimatedUsd()).max(BigDecimal.ZERO);
        actualUsd = actualUsd.add(actualCost);

        Counter.builder("ai.cost.actual")
                .tag("model", normalize(reservation.model()))
                .register(meterRegistry)
                .increment(actualCost.doubleValue());
    }

    public synchronized void release(CostReservation reservation, String reason) {
        reservedUsd = reservedUsd.subtract(reservation.estimatedUsd()).max(BigDecimal.ZERO);
        Counter.builder("ai.cost.reservation.released")
                .tag("reason", normalize(reason))
                .register(meterRegistry)
                .increment();
    }

    public int maxOutputTokens() {
        return maxOutputTokens;
    }

    public synchronized BigDecimal remainingBudgetUsd() {
        return monthlyBudgetUsd
                .subtract(actualUsd)
                .subtract(reservedUsd)
                .max(BigDecimal.ZERO);
    }

    private BigDecimal calculateCost(int inputTokens, int outputTokens) {
        BigDecimal inputCost = BigDecimal.valueOf(Math.max(inputTokens, 0))
                .multiply(inputUsdPer1kTokens)
                .divide(ONE_THOUSAND, 8, RoundingMode.HALF_UP);
        BigDecimal outputCost = BigDecimal.valueOf(Math.max(outputTokens, 0))
                .multiply(outputUsdPer1kTokens)
                .divide(ONE_THOUSAND, 8, RoundingMode.HALF_UP);
        return inputCost.add(outputCost);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (text.length() + CHARS_PER_ESTIMATED_TOKEN - 1) / CHARS_PER_ESTIMATED_TOKEN);
    }

    private static double reservedAmount(AiCostControlService control) {
        synchronized (control) {
            return control.reservedUsd.doubleValue();
        }
    }

    private static double actualAmount(AiCostControlService control) {
        synchronized (control) {
            return control.actualUsd.doubleValue();
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    public record CostReservation(String model, BigDecimal estimatedUsd) {
    }
}
