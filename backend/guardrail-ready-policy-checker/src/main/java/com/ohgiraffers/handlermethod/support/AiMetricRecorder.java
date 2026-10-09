package com.ohgiraffers.handlermethod.support;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class AiMetricRecorder {

    private final MeterRegistry meterRegistry;

    public AiMetricRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample start() {
        return Timer.start(meterRegistry);
    }

    public void stop(Timer.Sample sample) {
        sample.stop(Timer.builder("ai.chat.duration")
                .description("End-to-end AI chat request duration")
                .register(meterRegistry));
    }

    public void stopStage(Timer.Sample sample, String stage) {
        sample.stop(Timer.builder("ai.chat.stage.duration")
                .tag("stage", stage)
                .description("AI chat pipeline stage duration")
                .register(meterRegistry));
    }

    public void recordRequest(String outcome, String model) {
        Counter.builder("ai.chat.requests")
                .tag("outcome", outcome)
                .tag("model", model)
                .register(meterRegistry)
                .increment();
    }

    public void recordTokens(String model, int promptTokens, int completionTokens) {
        Counter.builder("ai.llm.tokens")
                .tag("type", "prompt")
                .tag("model", model)
                .register(meterRegistry)
                .increment(Math.max(promptTokens, 0));
        Counter.builder("ai.llm.tokens")
                .tag("type", "completion")
                .tag("model", model)
                .register(meterRegistry)
                .increment(Math.max(completionTokens, 0));
    }

    public void recordStage(String stage, String outcome) {
        Counter.builder("ai.chat.stage")
                .tag("stage", stage)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    public void recordFailure(String stage, String errorType) {
        Counter.builder("ai.chat.errors")
                .tag("stage", stage)
                .tag("error", errorType)
                .register(meterRegistry)
                .increment();
    }
}
