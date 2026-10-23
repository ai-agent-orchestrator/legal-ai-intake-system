package com.ohgiraffers.handlermethod.cost;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

class AiCostLifecycleTest {
    static class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-10-20T14:59:59Z");
        public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }
    AiCostControlService service(SimpleMeterRegistry registry, Clock clock) {
        return new AiCostControlService(new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("10"),
                new BigDecimal("0.15"), new BigDecimal("0.60"), 256, 1, 10, registry, clock);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void lateCompletionBelongsToReservationDayAndCannotConsumeAnotherDaysReservation(boolean reported) {
        var clock = new MutableClock(); var registry = new SimpleMeterRegistry(); var cost = service(registry, clock);
        var old = cost.reserveWithBlock("", "민사", "test", "old").reservation();
        assertThat(old.reservedDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        clock.now = clock.now.plusSeconds(2);
        var today = cost.reserveWithBlock("", "민사", "test", "new").reservation();
        cost.recordUsage(old, 10, 4, reported);
        var past = cost.dailyCost(old.reservedDate());
        assertThat(past.reserved()).isEqualByComparingTo("0");
        assertThat(reported ? past.reported() : past.estimated()).isEqualByComparingTo(
                reported ? new BigDecimal("0.0039") : old.estimatedUsd());
        var current = cost.dailyCost(today.reservedDate());
        assertThat(current.reserved()).isEqualByComparingTo(today.estimatedUsd());
        assertThat(current.reported()).isEqualByComparingTo("0");
        assertThat(current.estimated()).isEqualByComparingTo("0");
        assertThat(registry.get("ai.cost.daily.actual.usd").gauge().value()).isZero();
        assertThat(registry.get("ai.cost.daily.estimated.usage.usd").gauge().value()).isZero();
        cost.release(today, "not started");
        assertThat(registry.get("ai.cost.reserved.usd").gauge().value()).isZero();
    }

    @Test void dailyGaugesRollOverEvenWithoutANewRequest() {
        var clock = new MutableClock(); var registry = new SimpleMeterRegistry(); var cost = service(registry, clock);
        var reservation = cost.reserveWithBlock("", "민사", "test", "session").reservation();
        cost.recordUsage(reservation, 10, 4, true);
        clock.now = clock.now.plusSeconds(2);
        assertThat(registry.get("ai.cost.daily.actual.usd").gauge().value()).isZero();
        assertThat(cost.dailyCost(reservation.reservedDate()).reported()).isEqualByComparingTo("0.0039");
    }

    @Test void concurrentCompletionErrorAndReleaseCloseExactlyOnceAndKeepTheWinningSource() throws Exception {
        var registry = new SimpleMeterRegistry(); var cost = service(registry, new MutableClock());
        var reservation = cost.reserveWithBlock("", "민사", "test", "session").reservation();
        var start = new CountDownLatch(1); var ready = new CountDownLatch(60);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = new ArrayList<Future<?>>();
            for (int i = 0; i < 60; i++) {
                final int kind = i % 3;
                tasks.add(pool.submit(() -> {
                    ready.countDown(); start.await();
                    if (kind == 0) cost.recordUsage(reservation, 10, 4, true);
                    else if (kind == 1) cost.recordUsage(reservation, 0, 0, false);
                    else cost.release(reservation, "not started");
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            for (var task : tasks) task.get(5, TimeUnit.SECONDS);
        }
        var state = reservation.state();
        assertThat(state).isNotEqualTo(AiCostControlService.ReservationState.OPEN);
        var expectedSource = switch (state) {
            case SETTLED_REPORTED -> AiCostControlService.CostSource.REPORTED;
            case SETTLED_ESTIMATED -> AiCostControlService.CostSource.ESTIMATED;
            default -> AiCostControlService.CostSource.RELEASED;
        };
        assertThat(cost.recordUsage(reservation, 1000, 1000, true)).isEqualTo(expectedSource);
        cost.release(reservation, "duplicate");
        assertThat(reservation.state()).isEqualTo(state);
        assertThat(registry.get("ai.cost.reserved.usd").gauge().value()).isZero();
        double expected = state == AiCostControlService.ReservationState.SETTLED_REPORTED ? 0.0039
                : state == AiCostControlService.ReservationState.SETTLED_ESTIMATED ? reservation.estimatedUsd().doubleValue() : 0;
        assertThat(registry.get("ai.cost.accounted.usd").gauge().value()).isEqualTo(expected);
    }
}
