package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.cost.AiCostControlService;
import com.ohgiraffers.handlermethod.dto.AiChatRequest;
import com.ohgiraffers.handlermethod.dto.AiStreamEvent;
import com.ohgiraffers.handlermethod.guardrail.InputGuardrailClient;
import com.ohgiraffers.handlermethod.guardrail.OutputGuardrailClient;
import com.ohgiraffers.handlermethod.llm.StreamingLlmClient;
import com.ohgiraffers.handlermethod.support.AiMetricRecorder;
import com.ohgiraffers.handlermethod.support.TraceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AiStreamingService {

    private static final String OUTPUT_POLICY_FALLBACK = "OUTPUT_POLICY_BLOCKED";
    private static final String COST_LIMIT_FALLBACK = "COST_LIMIT_EXCEEDED";
    private static final String INPUT_POLICY_FALLBACK = "INPUT_GUARDRAIL_BLOCKED";
    private static final int CHARS_PER_TOKEN = 4;

    private final InputGuardrailClient inputGuardrailClient;
    private final OutputGuardrailClient outputGuardrailClient;
    private final StreamingLlmClient llmClient;
    private final AiCostControlService costControlService;
    private final AiMetricRecorder metricRecorder;
    private final String systemPrompt;
    private final String configuredModel;
    private final int chunkSize;
    private final int contextSize;

    public AiStreamingService(
            InputGuardrailClient inputGuardrailClient,
            OutputGuardrailClient outputGuardrailClient,
            StreamingLlmClient llmClient,
            AiCostControlService costControlService,
            AiMetricRecorder metricRecorder,
            @Value("${llm.system-prompt}") String systemPrompt,
            @Value("${llm.model}") String configuredModel,
            @Value("${llm.streaming.chunk-size:50}") int chunkSize,
            @Value("${llm.streaming.context-size:20}") int contextSize
    ) {
        this.inputGuardrailClient = inputGuardrailClient;
        this.outputGuardrailClient = outputGuardrailClient;
        this.llmClient = llmClient;
        this.costControlService = costControlService;
        this.metricRecorder = metricRecorder;
        this.systemPrompt = systemPrompt;
        this.configuredModel = configuredModel;
        this.chunkSize = chunkSize;
        this.contextSize = contextSize;
    }

    public void stream(AiChatRequest request, SseEmitter emitter) {
        String traceId = TraceContext.currentTraceId();
        AtomicBoolean settled = new AtomicBoolean(false);

        try {
            var input = inputGuardrailClient.check(request.message());
            metricRecorder.recordStage("stream_input_guardrail", input.status());
            if (!input.allowed()) {
                sendTerminal(emitter, "blocked", AiStreamEvent.blocked(
                        traceId, configuredModel, INPUT_POLICY_FALLBACK));
                metricRecorder.recordRequest("stream_input_blocked", configuredModel);
                settled.set(true);
                return;
            }

            var reservation = costControlService.reserve(
                    systemPrompt, input.content(), configuredModel);
            if (reservation.isEmpty()) {
                sendTerminal(emitter, "blocked", AiStreamEvent.fallback(
                        traceId, configuredModel, COST_LIMIT_FALLBACK));
                metricRecorder.recordRequest("stream_cost_blocked", configuredModel);
                settled.set(true);
                return;
            }

            send(emitter, "meta", new AiStreamEvent(
                    traceId, null, false, false, configuredModel,
                    "OUTPUT_RAIL_BUFFERED", null, null));

            StringBuilder pending = new StringBuilder();
            StringBuilder context = new StringBuilder();

            llmClient.stream(
                    systemPrompt,
                    input.content(),
                    chunk -> {
                        if (settled.get()) {
                            return;
                        }
                        pending.append(chunk);
                        if (estimatedTokens(pending) >= chunkSize) {
                            if (!flushChunk(emitter, traceId, configuredModel, pending, context, settled)) {
                                costControlService.release(reservation.get(), OUTPUT_POLICY_FALLBACK);
                                settled.set(true);
                            }
                        }
                    },
                    usage -> {
                        if (settled.get()) {
                            return;
                        }
                        if (!pending.isEmpty()
                                && !flushChunk(emitter, traceId, configuredModel, pending, context, settled)) {
                            costControlService.release(reservation.get(), OUTPUT_POLICY_FALLBACK);
                            settled.set(true);
                            return;
                        }
                        costControlService.recordActualUsage(
                                reservation.get(), usage.promptTokens(), usage.completionTokens());
                        metricRecorder.recordTokens(
                                usage.model(), usage.promptTokens(), usage.completionTokens());
                        metricRecorder.recordRequest("stream_success", usage.model());
                        sendTerminal(emitter, "done", AiStreamEvent.terminal(
                                traceId, usage.model(), usage.promptTokens(), usage.completionTokens()));
                        settled.set(true);
                    },
                    error -> {
                        if (settled.compareAndSet(false, true)) {
                            costControlService.release(reservation.get(), "STREAM_FAILURE");
                            metricRecorder.recordFailure("stream_llm", error.getClass().getSimpleName());
                            sendTerminal(emitter, "error", AiStreamEvent.fallback(
                                    traceId, configuredModel, "LLM_STREAM_UNAVAILABLE"));
                        }
                    }
            );
        } catch (RuntimeException exception) {
            if (settled.compareAndSet(false, true)) {
                metricRecorder.recordFailure("stream_input_guardrail", exception.getClass().getSimpleName());
                sendTerminal(emitter, "error", AiStreamEvent.fallback(
                        traceId, configuredModel, "STREAM_PIPELINE_UNAVAILABLE"));
            }
        }
    }

    private boolean flushChunk(
            SseEmitter emitter,
            String traceId,
            String model,
            StringBuilder pending,
            StringBuilder context,
            AtomicBoolean settled
    ) {
        String text = pending.toString();
        String guardedInput = context + text;
        var result = outputGuardrailClient.check(guardedInput);
        metricRecorder.recordStage("stream_output_guardrail", result.status());

        if (!result.allowed()) {
            sendTerminal(emitter, "blocked", AiStreamEvent.blocked(
                    traceId, model, OUTPUT_POLICY_FALLBACK));
            metricRecorder.recordRequest("stream_output_blocked", model);
            settled.set(true);
            return false;
        }

        send(emitter, "token", AiStreamEvent.token(traceId, model, text));
        pending.setLength(0);
        context.setLength(0);
        String contextText = guardedInput;
        int maxChars = contextSize * CHARS_PER_TOKEN;
        if (contextText.length() > maxChars) {
            contextText = contextText.substring(contextText.length() - maxChars);
        }
        context.append(contextText);
        return true;
    }

    private int estimatedTokens(StringBuilder value) {
        return Math.max(1, (value.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN);
    }

    private void sendTerminal(SseEmitter emitter, String event, AiStreamEvent data) {
        send(emitter, event, data);
        emitter.complete();
    }

    private void send(SseEmitter emitter, String event, AiStreamEvent data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }
}
