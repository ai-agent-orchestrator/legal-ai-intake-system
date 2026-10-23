package com.ohgiraffers.handlermethod.cost;

import com.ohgiraffers.handlermethod.support.AiSessionIds;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;
import java.util.EnumMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AiCostControlService {

    private static final BigDecimal ONE_THOUSAND = BigDecimal.valueOf(1_000);
    private static final int CHAT_MESSAGE_OVERHEAD_TOKENS = 32;

    private final BigDecimal monthlyBudgetUsd;
    private final BigDecimal dailyBudgetUsd;
    private final BigDecimal sessionBudgetUsd;
    private final BigDecimal inputUsdPer1kTokens;
    private final BigDecimal outputUsdPer1kTokens;
    private final int maxOutputTokens;
    private final int maxInputPages;
    private final int estimatedCharsPerPage;
    private final MeterRegistry meterRegistry;
    private final Map<CostStage, StageAmounts> stages = new EnumMap<>(CostStage.class);

    private BigDecimal reservedUsd = BigDecimal.ZERO;
    private BigDecimal actualUsd = BigDecimal.ZERO;
    private BigDecimal estimatedUsageUsd = BigDecimal.ZERO;
    private BigDecimal dailyReservedUsd = BigDecimal.ZERO;
    private BigDecimal dailyActualUsd = BigDecimal.ZERO;
    private BigDecimal dailyEstimatedUsageUsd = BigDecimal.ZERO;
    private LocalDate currentBudgetDate;
    private final Clock clock;
    private final Map<LocalDate, DailyAmounts> dailyLedger = new java.util.HashMap<>();
    private final Map<String, BigDecimal> sessionReservedUsd = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> sessionActualUsd = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> sessionEstimatedUsageUsd = new ConcurrentHashMap<>();

    @Autowired
    public AiCostControlService(
            @Value("${llm.cost.monthly-budget-usd}") BigDecimal monthlyBudgetUsd,
            @Value("${llm.cost.daily-budget-usd}") BigDecimal dailyBudgetUsd,
            @Value("${llm.cost.session-budget-usd}") BigDecimal sessionBudgetUsd,
            @Value("${llm.cost.input-usd-per-1k-tokens}") BigDecimal inputUsdPer1kTokens,
            @Value("${llm.cost.output-usd-per-1k-tokens}") BigDecimal outputUsdPer1kTokens,
            @Value("${llm.max-output-tokens}") int maxOutputTokens,
            @Value("${llm.cost.max-input-pages}") int maxInputPages,
            @Value("${llm.cost.estimated-chars-per-page}") int estimatedCharsPerPage,
            MeterRegistry meterRegistry
    ) {
        this(monthlyBudgetUsd, dailyBudgetUsd, sessionBudgetUsd, inputUsdPer1kTokens, outputUsdPer1kTokens,
                maxOutputTokens, maxInputPages, estimatedCharsPerPage, meterRegistry, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    AiCostControlService(BigDecimal monthlyBudgetUsd, BigDecimal dailyBudgetUsd, BigDecimal sessionBudgetUsd,
            BigDecimal inputUsdPer1kTokens, BigDecimal outputUsdPer1kTokens, int maxOutputTokens,
            int maxInputPages, int estimatedCharsPerPage, MeterRegistry meterRegistry, Clock clock) {
        if (maxOutputTokens < 1 || maxInputPages < 1 || estimatedCharsPerPage < 1)
            throw new IllegalArgumentException("Input/output limits must be positive");
        this.clock = clock;
        this.currentBudgetDate = LocalDate.now(clock);
        this.monthlyBudgetUsd = monthlyBudgetUsd;
        this.dailyBudgetUsd = dailyBudgetUsd;
        this.sessionBudgetUsd = sessionBudgetUsd;
        this.inputUsdPer1kTokens = inputUsdPer1kTokens;
        this.outputUsdPer1kTokens = outputUsdPer1kTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.maxInputPages = maxInputPages;
        this.estimatedCharsPerPage = estimatedCharsPerPage;
        this.meterRegistry = meterRegistry;
        for (CostStage stage : CostStage.values()) {
            stages.put(stage, new StageAmounts());
            Gauge.builder("ai.cost.stage.reserved.usd", this, c -> c.stageAmount(stage, "reserved"))
                    .tag("stage", stage.tag()).register(meterRegistry);
            Gauge.builder("ai.cost.stage.actual.usd", this, c -> c.stageAmount(stage, "actual"))
                    .tag("stage", stage.tag()).register(meterRegistry);
            Gauge.builder("ai.cost.stage.estimated.usage.usd", this, c -> c.stageAmount(stage, "estimated"))
                    .tag("stage", stage.tag()).register(meterRegistry);
            Gauge.builder("ai.cost.stage.accounted.usd", this, c -> c.stageAmount(stage, "accounted"))
                    .tag("stage", stage.tag()).register(meterRegistry);
        }

        Gauge.builder("ai.cost.budget.usd", this, control -> control.monthlyBudgetUsd.doubleValue())
                .description("Configured application-side AI budget")
                .register(meterRegistry);
        Gauge.builder("ai.cost.daily.budget.usd", this, control -> control.dailyBudgetUsd.doubleValue())
                .description("Configured application-side daily AI budget")
                .register(meterRegistry);
        Gauge.builder("ai.cost.session.budget.usd", this, control -> control.sessionBudgetUsd.doubleValue())
                .description("Configured application-side per-session AI budget")
                .register(meterRegistry);
        Gauge.builder("ai.cost.reserved.usd", this, AiCostControlService::reservedAmount)
                .description("Estimated cost reserved by in-flight AI calls")
                .register(meterRegistry);
        Gauge.builder("ai.cost.actual.usd", this, AiCostControlService::actualAmount)
                .description("Actual recorded AI cost for the current application period")
                .register(meterRegistry);
        Gauge.builder("ai.cost.daily.actual.usd", this, AiCostControlService::dailyActualAmount)
                .description("Actual recorded AI cost for the current application day")
                .register(meterRegistry);
        Gauge.builder("ai.cost.estimated.usage.usd", this, AiCostControlService::estimatedUsageAmount)
                .description("Settled estimated cost when provider token usage was unavailable")
                .register(meterRegistry);
        Gauge.builder("ai.cost.daily.estimated.usage.usd", this, AiCostControlService::dailyEstimatedUsageAmount)
                .description("Current-day estimated settlements for unavailable token usage")
                .register(meterRegistry);
        Gauge.builder("ai.cost.accounted.usd", this, AiCostControlService::accountedAmount)
                .description("Reported-usage cost plus settled estimated-usage cost")
                .register(meterRegistry);
    }

    public Optional<CostLimitBlock> checkInputPageLimit(String userMessage) {
        int chars = userMessage == null ? 0 : userMessage.length();
        int effectiveMaxInputPages = maxInputPages;
        long maxChars = (long) effectiveMaxInputPages * estimatedCharsPerPage;

        if (chars <= maxChars) {
            return Optional.empty();
        }

        int estimatedPages = (int) Math.max(1, (chars + (long) estimatedCharsPerPage - 1) / estimatedCharsPerPage);
        String detailCode = "REQUEST_TOO_LONG";
        Counter.builder("ai.cost.blocked")
                .tag("reason", detailCode)
                .tag("model", "pre_llm")
                .register(meterRegistry)
                .increment();

        return Optional.of(new CostLimitBlock(
                "COST_LIMIT_BLOCKED",
                detailCode,
                estimatedPages,
                effectiveMaxInputPages,
                "현재 상황은 최대 %,d자까지 입력할 수 있습니다. 사건 경위, 날짜, 금액, 보유 자료를 중심으로 정리해 주세요."
                        .formatted(maxChars)
        ));
    }

    public synchronized Optional<CostReservation> reserve(String systemPrompt,
                                                           String userMessage,
                                                           String model) {
        return Optional.ofNullable(reserveWithBlock(systemPrompt, userMessage, model, null).reservation());
    }

    public synchronized CostReservationDecision reserveWithBlock(String systemPrompt,
                                                                  String userMessage,
                                                                  String model,
                                                                  String sessionId) {
        return reserveStageWithBlock(CostStage.ANSWER, estimateChatInputTokens(systemPrompt, userMessage),
                maxOutputTokens, model, sessionId, inputUsdPer1kTokens, outputUsdPer1kTokens);
    }

    public synchronized CostReservationDecision reserveStageWithBlock(CostStage stage, int inputTokens,
            int outputTokens, String model, String sessionId, BigDecimal inputRate, BigDecimal outputRate) {
        if (inputTokens < 0 || outputTokens < 0 || inputRate.signum() < 0 || outputRate.signum() < 0) {
            throw new IllegalArgumentException("Cost estimates and rates must be nonnegative");
        }
        resetDailyBudgetIfNeeded();
        String normalizedSessionId = AiSessionIds.normalize(sessionId);
        BigDecimal estimatedUsd = calculateCost(inputTokens, outputTokens, inputRate, outputRate);

        if (actualUsd.add(estimatedUsageUsd).add(reservedUsd).add(estimatedUsd).compareTo(monthlyBudgetUsd) > 0) {
            return blockedBudget("MONTHLY_BUDGET_EXCEEDED", model, stage);
        }

        if (dailyActualUsd.add(dailyEstimatedUsageUsd).add(dailyReservedUsd).add(estimatedUsd).compareTo(dailyBudgetUsd) > 0) {
            return blockedBudget("DAILY_BUDGET_EXCEEDED", model, stage);
        }

        BigDecimal sessionUsed = sessionActualUsd.getOrDefault(normalizedSessionId, BigDecimal.ZERO)
                .add(sessionEstimatedUsageUsd.getOrDefault(normalizedSessionId, BigDecimal.ZERO))
                .add(sessionReservedUsd.getOrDefault(normalizedSessionId, BigDecimal.ZERO));
        if (sessionUsed.add(estimatedUsd).compareTo(sessionBudgetUsd) > 0) {
            return blockedBudget("SESSION_BUDGET_EXCEEDED", model, stage);
        }

        reservedUsd = reservedUsd.add(estimatedUsd);
        dailyReservedUsd = dailyReservedUsd.add(estimatedUsd);
        daily(currentBudgetDate).reserved = daily(currentBudgetDate).reserved.add(estimatedUsd);
        sessionReservedUsd.merge(normalizedSessionId, estimatedUsd, BigDecimal::add);
        stages.get(stage).reserved = stages.get(stage).reserved.add(estimatedUsd);
        Counter.builder("ai.cost.estimated")
                .tag("model", normalize(model))
                .tag("stage", stage.tag())
                .register(meterRegistry)
                .increment(estimatedUsd.doubleValue());

        return CostReservationDecision.allowed(
                new CostReservation(model, normalizedSessionId, currentBudgetDate, estimatedUsd,
                        stage, inputRate, outputRate, new ReservationCompletion()));
    }

    public synchronized CostSource recordUsage(CostReservation reservation, int promptTokens,
                                               int completionTokens, boolean usageAvailable) {
        CostSource source = usageAvailable && promptTokens >= 0 && completionTokens >= 0
                ? CostSource.REPORTED : CostSource.ESTIMATED;
        // Timeout, disconnect and provider callbacks can race. A reservation closes once.
        if (!reservation.completion().state.compareAndSet(ReservationState.OPEN,
                source == CostSource.REPORTED ? ReservationState.SETTLED_REPORTED : ReservationState.SETTLED_ESTIMATED)) {
            return switch (reservation.state()) {
                case SETTLED_REPORTED -> CostSource.REPORTED;
                case SETTLED_ESTIMATED -> CostSource.ESTIMATED;
                default -> CostSource.RELEASED;
            };
        }
        if (usageAvailable && promptTokens >= 0 && completionTokens >= 0) {
            settleReportedUsage(reservation, promptTokens, completionTokens);
            return CostSource.REPORTED;
        }
        resetDailyBudgetIfNeeded();
        releaseReservedAmounts(reservation);
        estimatedUsageUsd = estimatedUsageUsd.add(reservation.estimatedUsd());
        daily(reservation.reservedDate()).estimated = daily(reservation.reservedDate()).estimated.add(reservation.estimatedUsd());
        StageAmounts stage = stages.get(reservation.stage());
        stage.estimated = stage.estimated.add(reservation.estimatedUsd());
        if (reservation.reservedDate().equals(currentBudgetDate)) {
            dailyEstimatedUsageUsd = dailyEstimatedUsageUsd.add(reservation.estimatedUsd());
        }
        sessionEstimatedUsageUsd.merge(reservation.sessionId(), reservation.estimatedUsd(), BigDecimal::add);
        Counter.builder("ai.cost.usage.missing")
                .tag("model", normalize(reservation.model()))
                .tag("stage", reservation.stage().tag())
                .register(meterRegistry).increment();
        Counter.builder("ai.cost.usage.estimated")
                .tag("model", normalize(reservation.model()))
                .tag("stage", reservation.stage().tag())
                .register(meterRegistry).increment(reservation.estimatedUsd().doubleValue());
        return CostSource.ESTIMATED;
    }

    public synchronized void recordActualUsage(CostReservation reservation,
                                                int promptTokens,
                                                int completionTokens) {
        recordUsage(reservation, promptTokens, completionTokens, true);
    }

    private void settleReportedUsage(CostReservation reservation, int promptTokens, int completionTokens) {
        resetDailyBudgetIfNeeded();
        BigDecimal actualCost = calculateCost(promptTokens, completionTokens,
                reservation.inputRate(), reservation.outputRate());
        releaseReservedAmounts(reservation);
        actualUsd = actualUsd.add(actualCost);
        daily(reservation.reservedDate()).actual = daily(reservation.reservedDate()).actual.add(actualCost);
        StageAmounts stage = stages.get(reservation.stage());
        stage.actual = stage.actual.add(actualCost);
        if (reservation.reservedDate().equals(currentBudgetDate)) {
            dailyActualUsd = dailyActualUsd.add(actualCost);
        }
        sessionActualUsd.merge(reservation.sessionId(), actualCost, BigDecimal::add);

        Counter.builder("ai.cost.actual")
                .tag("model", normalize(reservation.model()))
                .tag("stage", reservation.stage().tag())
                .register(meterRegistry)
                .increment(actualCost.doubleValue());
    }

    public synchronized void release(CostReservation reservation, String reason) {
        if (!reservation.completion().state.compareAndSet(ReservationState.OPEN, ReservationState.RELEASED)) {
            return;
        }
        resetDailyBudgetIfNeeded();
        releaseReservedAmounts(reservation);
        Counter.builder("ai.cost.reservation.released")
                .tag("reason", normalize(reason))
                .tag("stage", reservation.stage().tag())
                .register(meterRegistry)
                .increment();
    }

    public int maxOutputTokens() {
        return maxOutputTokens;
    }

    public synchronized BigDecimal remainingBudgetUsd() {
        return monthlyBudgetUsd
                .subtract(actualUsd)
                .subtract(estimatedUsageUsd)
                .subtract(reservedUsd)
                .max(BigDecimal.ZERO);
    }

    private CostReservationDecision blockedBudget(String reason, String model, CostStage stage) {
        Counter.builder("ai.cost.blocked")
                .tag("reason", reason)
                .tag("model", normalize(model))
                .tag("stage", stage.tag())
                .register(meterRegistry)
                .increment();

        return CostReservationDecision.blocked(new CostLimitBlock(
                "COST_LIMIT_BLOCKED",
                reason,
                1,
                1,
                """
                        현재 AI 사용 예산 한도에 도달해 다음 유료 처리 단계를 실행하지 않았습니다.
                        비용 절감을 위해 요청을 더 짧게 나누거나 잠시 후 다시 시도해 주세요.
                        """
        ));
    }

    private void releaseReservedAmounts(CostReservation reservation) {
        reservedUsd = reservedUsd.subtract(reservation.estimatedUsd()).max(BigDecimal.ZERO);
        DailyAmounts day = daily(reservation.reservedDate());
        day.reserved = day.reserved.subtract(reservation.estimatedUsd()).max(BigDecimal.ZERO);
        StageAmounts stage = stages.get(reservation.stage());
        stage.reserved = stage.reserved.subtract(reservation.estimatedUsd()).max(BigDecimal.ZERO);
        if (reservation.reservedDate().equals(currentBudgetDate)) {
            dailyReservedUsd = dailyReservedUsd.subtract(reservation.estimatedUsd()).max(BigDecimal.ZERO);
        }
        sessionReservedUsd.computeIfPresent(reservation.sessionId(),
                (ignored, amount) -> amount.subtract(reservation.estimatedUsd()).max(BigDecimal.ZERO));
    }

    private void resetDailyBudgetIfNeeded() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(currentBudgetDate)) {
            currentBudgetDate = today;
            DailyAmounts day = daily(today);
            dailyReservedUsd = day.reserved;
            dailyActualUsd = day.actual;
            dailyEstimatedUsageUsd = day.estimated;
        }
    }

    private BigDecimal calculateCost(int inputTokens, int outputTokens, BigDecimal inputRate, BigDecimal outputRate) {
        BigDecimal inputCost = BigDecimal.valueOf(Math.max(inputTokens, 0))
                .multiply(inputRate)
                .divide(ONE_THOUSAND, 8, RoundingMode.HALF_UP);
        BigDecimal outputCost = BigDecimal.valueOf(Math.max(outputTokens, 0))
                .multiply(outputRate)
                .divide(ONE_THOUSAND, 8, RoundingMode.HALF_UP);
        return inputCost.add(outputCost);
    }

    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // Conservative reservation for Korean/mixed-language text, not an exact model tokenizer.
        // Actual reported prompt/completion usage replaces this estimate at settlement.
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    public static int estimateChatInputTokens(String systemPrompt, String userMessage) {
        return Math.addExact(CHAT_MESSAGE_OVERHEAD_TOKENS,
                Math.addExact(estimateTokens(systemPrompt), estimateTokens(userMessage)));
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

    private static double dailyActualAmount(AiCostControlService control) {
        synchronized (control) {
            control.resetDailyBudgetIfNeeded();
            return control.dailyActualUsd.doubleValue();
        }
    }

    private static double estimatedUsageAmount(AiCostControlService control) {
        synchronized (control) {
            return control.estimatedUsageUsd.doubleValue();
        }
    }

    private static double dailyEstimatedUsageAmount(AiCostControlService control) {
        synchronized (control) {
            control.resetDailyBudgetIfNeeded();
            return control.dailyEstimatedUsageUsd.doubleValue();
        }
    }

    private static double accountedAmount(AiCostControlService control) {
        synchronized (control) {
            return control.actualUsd.add(control.estimatedUsageUsd).doubleValue();
        }
    }

    public enum CostSource {
        REPORTED, ESTIMATED, RELEASED
    }

    public enum CostStage {
        INPUT_GUARDRAIL("input_guardrail"), ANSWER("answer"), OUTPUT_GUARDRAIL("output_guardrail"),
        SEARCH_QUERY("search_query"), SEARCH_EXPLANATION("search_explanation");
        private final String tag;
        CostStage(String tag) { this.tag = tag; }
        public String tag() { return tag; }
    }

    private synchronized double stageAmount(CostStage stage, String kind) {
        StageAmounts amounts = stages.get(stage);
        return switch (kind) {
            case "reserved" -> amounts.reserved.doubleValue();
            case "actual" -> amounts.actual.doubleValue();
            case "estimated" -> amounts.estimated.doubleValue();
            default -> amounts.actual.add(amounts.estimated).doubleValue();
        };
    }

    private static class StageAmounts {
        BigDecimal reserved = BigDecimal.ZERO;
        BigDecimal actual = BigDecimal.ZERO;
        BigDecimal estimated = BigDecimal.ZERO;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    public enum ReservationState { OPEN, SETTLED_REPORTED, SETTLED_ESTIMATED, RELEASED }

    public static final class ReservationCompletion {
        private final AtomicReference<ReservationState> state = new AtomicReference<>(ReservationState.OPEN);
    }

    public record CostReservation(String model, String sessionId, LocalDate reservedDate, BigDecimal estimatedUsd,
                                  CostStage stage, BigDecimal inputRate, BigDecimal outputRate, ReservationCompletion completion) {
        public ReservationState state() { return completion.state.get(); }
    }

    private DailyAmounts daily(LocalDate date) { return dailyLedger.computeIfAbsent(date, ignored -> new DailyAmounts()); }
    private static final class DailyAmounts {
        BigDecimal reserved = BigDecimal.ZERO, actual = BigDecimal.ZERO, estimated = BigDecimal.ZERO;
    }
    public record DailyCost(BigDecimal reserved, BigDecimal reported, BigDecimal estimated) { }
    public synchronized DailyCost dailyCost(LocalDate date) {
        DailyAmounts day = daily(date);
        return new DailyCost(day.reserved, day.actual, day.estimated);
    }

    public record CostReservationDecision(
            CostReservation reservation,
            CostLimitBlock block
    ) {

        public static CostReservationDecision allowed(CostReservation reservation) {
            return new CostReservationDecision(reservation, null);
        }

        public static CostReservationDecision blocked(CostLimitBlock block) {
            return new CostReservationDecision(null, block);
        }

        public boolean allowed() {
            return reservation != null;
        }
    }

    public record CostLimitBlock(
            String reasonCode,
            String detailCode,
            int estimatedPages,
            int maxPages,
            String safeResponse
    ) {
    }
}
