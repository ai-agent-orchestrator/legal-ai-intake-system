package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.cost.AiCostControlService;
import com.ohgiraffers.handlermethod.cost.CostedGuardrailService;
import com.ohgiraffers.handlermethod.cost.CostLimitException;
import com.ohgiraffers.handlermethod.dto.AiChatRequest;
import com.ohgiraffers.handlermethod.dto.AiStreamEvent;
import com.ohgiraffers.handlermethod.llm.StreamingLlmClient;
import com.ohgiraffers.handlermethod.llm.LlmStreamControl;
import com.ohgiraffers.handlermethod.riskengine.LegalDynamicGuardService;
import com.ohgiraffers.handlermethod.riskengine.LegalFixedGuardService;
import com.ohgiraffers.handlermethod.riskengine.PrivacyOutputGuardService;
import com.ohgiraffers.handlermethod.support.AiMetricRecorder;
import com.ohgiraffers.handlermethod.support.TraceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AiStreamingService {

    private static final String OUTPUT_POLICY_FALLBACK = "OUTPUT_POLICY_BLOCKED";
    private static final String INPUT_POLICY_FALLBACK = "INPUT_GUARDRAIL_BLOCKED";
    private static final int CHARS_PER_TOKEN = 4;

    private final CostedGuardrailService guardrails;
    private final StreamingLlmClient llmClient;
    private final AiCostControlService costControlService;
    private final AiMetricRecorder metricRecorder;
    private final LegalFixedGuardService legalFixedGuardService;
    private final LegalDynamicGuardService legalDynamicGuardService;
    private final PrivacyOutputGuardService privacyOutputGuardService;
    private final String systemPrompt;
    private final String configuredModel;
    private final int chunkSize;

    public AiStreamingService(
            CostedGuardrailService guardrails,
            StreamingLlmClient llmClient,
            AiCostControlService costControlService,
            AiMetricRecorder metricRecorder,
            LegalFixedGuardService legalFixedGuardService,
            LegalDynamicGuardService legalDynamicGuardService,
            PrivacyOutputGuardService privacyOutputGuardService,
            @Value("${llm.system-prompt}") String systemPrompt,
            @Value("${llm.model}") String configuredModel,
            @Value("${llm.streaming.chunk-size:50}") int chunkSize
    ) {
        this.guardrails = guardrails;
        this.llmClient = llmClient;
        this.costControlService = costControlService;
        this.metricRecorder = metricRecorder;
        this.legalFixedGuardService = legalFixedGuardService;
        this.legalDynamicGuardService = legalDynamicGuardService;
        this.privacyOutputGuardService = privacyOutputGuardService;
        this.systemPrompt = systemPrompt;
        this.configuredModel = configuredModel;
        this.chunkSize = chunkSize;
    }

    public void stream(AiChatRequest request, SseEmitter emitter) {
        String traceId = TraceContext.currentTraceId();
        AtomicBoolean settled = new AtomicBoolean(false);
        AtomicReference<AiCostControlService.CostReservation> inFlight = new AtomicReference<>();
        AtomicBoolean disconnected = new AtomicBoolean(false);
        LlmStreamControl control = new LlmStreamControl();
        StringBuilder pending = new StringBuilder();
        Runnable abandoned = () -> {
            disconnected.set(true);
            settled.compareAndSet(false, true);
            var snapshot = control.cancel();
            settleInterrupted(inFlight.get(), snapshot);
            synchronized (pending) { pending.setLength(0); }
        };
        emitter.onTimeout(() -> { abandoned.run(); emitter.complete(); });
        emitter.onError(error -> abandoned.run());
        emitter.onCompletion(abandoned);

        try {
            var preCheck = legalDynamicGuardService.preCheck(request.sessionId());
            if (preCheck.blocked()) {
                metricRecorder.recordRequest("stream_dynamic_guard_blocked", configuredModel);
                metricRecorder.recordDynamicBlock(preCheck.reasonCode());
                metricRecorder.recordLlmCallPrevented(preCheck.reasonCode());
                sendTerminal(emitter, "blocked", AiStreamEvent.blocked(
                        traceId, configuredModel, preCheck.safeResponse(),
                        preCheck.reasonCode(), preCheck.reasonCode(), null));
                settled.set(true);
                return;
            }

            var fixedGuard = legalFixedGuardService.check(request.message());
            if (!fixedGuard.allowed()) {
                metricRecorder.recordRequest("stream_fixed_guard_blocked", configuredModel);
                metricRecorder.recordLegalGuardrailBlock(fixedGuard.reasonCode());
                stopInput(request, emitter, traceId, settled, fixedGuard.safeResponse(),
                        fixedGuard.reasonCode(), fixedGuard.reasonCode(), null, fixedGuard.reasonCode());
                return;
            }

            var pageLimitBlock = costControlService.checkInputPageLimit(request.message());
            if (pageLimitBlock.isPresent()) {
                var block = pageLimitBlock.get();
                metricRecorder.recordRequest("stream_cost_blocked", configuredModel);
                metricRecorder.recordLegalCostBlock(block.detailCode());
                stopInput(request, emitter, traceId, settled, block.safeResponse(),
                        block.reasonCode(), block.reasonCode(), block.detailCode(), block.detailCode());
                return;
            }

            var input = guardrails.checkInput(request.message(), request.sessionId()).result();
            metricRecorder.recordStage("stream_input_guardrail", input.status());
            if (!input.allowed()) {
                metricRecorder.recordRequest("stream_input_blocked", configuredModel);
                metricRecorder.recordLegalGuardrailBlock("NEMO_INPUT_GUARDRAIL");
                stopInput(request, emitter, traceId, settled, input.content(),
                        INPUT_POLICY_FALLBACK, INPUT_POLICY_FALLBACK,
                        "NEMO_INPUT_GUARDRAIL", "NEMO_INPUT_GUARDRAIL");
                return;
            }

            if (disconnected.get()) return;
            var reservationDecision = costControlService.reserveWithBlock(
                    systemPrompt, input.content(), configuredModel, request.sessionId());
            if (!reservationDecision.allowed()) {
                var block = reservationDecision.block();
                sendTerminal(emitter, "blocked", AiStreamEvent.blocked(
                        traceId, configuredModel, block.safeResponse(),
                        block.detailCode(), block.reasonCode(), block.detailCode()));
                metricRecorder.recordRequest("stream_cost_blocked", configuredModel);
                metricRecorder.recordLegalCostBlock(reservationDecision.block().detailCode());
                metricRecorder.recordLlmCallPrevented(reservationDecision.block().reasonCode());
                settled.set(true);
                return;
            }
            AiCostControlService.CostReservation reservation = reservationDecision.reservation();
            inFlight.set(reservation);
            if (settled.get()) {
                settleInterrupted(reservation, control.cancel());
                return;
            }

            send(emitter, "meta", AiStreamEvent.buffered(traceId, configuredModel));

            llmClient.stream(
                    systemPrompt,
                    input.content(),
                    chunk -> {
                        if (settled.get()) {
                            return;
                        }
                        synchronized (pending) { if (!settled.get()) pending.append(chunk); }
                    },
                    usage -> {
                        control.reportUsage(usage);
                        var confirmed = control.snapshot().usage();
                        if (confirmed != null) usage = confirmed;
                        if (!settled.compareAndSet(false, true)) {
                            return;
                        }
                        // Provider work is charged even if output checks or SSE delivery fail.
                        var costSource = costControlService.recordUsage(
                                reservation, usage.promptTokens(), usage.completionTokens(), usage.usageAvailable());
                        if (usage.usageAvailable()) {
                            metricRecorder.recordTokens(usage.model(), usage.promptTokens(), usage.completionTokens());
                        }
                        if (disconnected.get()) return;
                        try {
                            if (!flushCompletedResponse(emitter, traceId, configuredModel, pending, request.sessionId(), disconnected, reservation, control)) {
                                return;
                            }
                            if (disconnected.get()) return;
                            metricRecorder.recordRequest("stream_success", usage.model());
                            sendTerminal(emitter, "done", AiStreamEvent.terminal(
                                    traceId, usage.model(),
                                    usage.usageAvailable() ? usage.promptTokens() : null,
                                    usage.usageAvailable() ? usage.completionTokens() : null,
                                    usage.usageAvailable(), costSource.name()));
                        } catch (CostLimitException exception) {
                            if (!disconnected.get()) sendCostBlock(emitter, traceId, exception, reservation, control);
                        } catch (RuntimeException exception) {
                            metricRecorder.recordFailure("stream_output_guardrail", exception.getClass().getSimpleName());
                            if (!disconnected.get()) sendTerminal(emitter, "error", AiStreamEvent.fallback(
                                    traceId, configuredModel, "STREAM_PIPELINE_UNAVAILABLE").withCost(
                                            usage.usageAvailable() ? usage.promptTokens() : null,
                                            usage.usageAvailable() ? usage.completionTokens() : null, usage.usageAvailable(), costSource.name()));
                        }
                    },
                    error -> {
                        if (settled.compareAndSet(false, true)) {
                            settleInterrupted(reservation, control.cancel());
                            metricRecorder.recordFailure("stream_llm", error.getClass().getSimpleName());
                            if (!disconnected.get()) sendTerminal(emitter, "error", costEvent(AiStreamEvent.fallback(
                                    traceId, configuredModel, "LLM_STREAM_UNAVAILABLE"), reservation, control));
                        }
                    }, control
            );
        } catch (CostLimitException exception) {
            if (settled.compareAndSet(false, true)) {
                settleInterrupted(inFlight.get(), control.cancel());
                if (!disconnected.get()) sendCostBlock(emitter, traceId, exception, inFlight.get(), control);
            }
        } catch (RuntimeException exception) {
            if (settled.compareAndSet(false, true)) {
                settleInterrupted(inFlight.get(), control.cancel());
                metricRecorder.recordFailure("stream_input_guardrail", exception.getClass().getSimpleName());
                if (!disconnected.get()) sendTerminal(emitter, "error", costEvent(AiStreamEvent.fallback(
                        traceId, configuredModel, "STREAM_PIPELINE_UNAVAILABLE"), inFlight.get(), control));
            }
        }
    }

    private void settleInterrupted(AiCostControlService.CostReservation reservation, LlmStreamControl.Snapshot snapshot) {
        if (reservation == null) { return; }
        if (snapshot.usage() != null) {
            var usage = snapshot.usage();
            costControlService.recordUsage(reservation, usage.promptTokens(), usage.completionTokens(), usage.usageAvailable());
        } else if (snapshot.dispatched()) {
            costControlService.recordUsage(reservation, 0, 0, false);
        } else {
            costControlService.release(reservation, "STREAM_NOT_STARTED");
        }
    }

    private void sendCostBlock(SseEmitter emitter, String traceId, CostLimitException exception,
                               AiCostControlService.CostReservation reservation, LlmStreamControl control) {
        var block = exception.block();
        metricRecorder.recordRequest("stream_cost_blocked", configuredModel);
        metricRecorder.recordLegalCostBlock(block.detailCode());
        sendTerminal(emitter, "blocked", costEvent(AiStreamEvent.blocked(traceId, configuredModel,
                block.safeResponse(), block.detailCode(), block.reasonCode(), block.detailCode()), reservation, control));
    }

    private void stopInput(AiChatRequest request, SseEmitter emitter, String traceId,
                           AtomicBoolean settled, String safeResponse, String legacyReason,
                           String reasonCode, String detailCode, String riskReason) {
        metricRecorder.recordLlmCallPrevented(reasonCode);
        var dynamic = legalDynamicGuardService.recordBlockedEvent(request.sessionId(), riskReason);
        if (dynamic.blocked()) {
            metricRecorder.recordRequest("stream_dynamic_guard_blocked", configuredModel);
            metricRecorder.recordDynamicBlock(dynamic.reasonCode());
            sendTerminal(emitter, "blocked", AiStreamEvent.blocked(
                    traceId, configuredModel, dynamic.safeResponse(),
                    dynamic.reasonCode(), dynamic.reasonCode(), riskReason));
        } else {
            sendTerminal(emitter, "blocked", AiStreamEvent.blocked(
                    traceId, configuredModel, safeResponse, legacyReason, reasonCode, detailCode));
        }
        settled.set(true);
    }

    private boolean flushCompletedResponse(
            SseEmitter emitter,
            String traceId,
            String model,
            StringBuilder pending,
            String sessionId, AtomicBoolean disconnected,
            AiCostControlService.CostReservation reservation, LlmStreamControl control
    ) {
        // Hold the complete answer so PII split across provider chunks cannot be sent early.
        if (disconnected.get()) return false;
        String answer;
        synchronized (pending) { answer = pending.toString(); }
        var result = guardrails.checkOutput(answer, sessionId).result();
        if (disconnected.get()) return false;
        metricRecorder.recordStage("stream_output_guardrail", result.status());

        if (!result.allowed()) {
            sendTerminal(emitter, "blocked", costEvent(AiStreamEvent.blocked(
                    traceId, model, OUTPUT_POLICY_FALLBACK), reservation, control));
            metricRecorder.recordRequest("stream_output_blocked", model);
            return false;
        }

        var privacy = privacyOutputGuardService.mask(result.content());
        if (privacy.masked()) {
            privacy.piiTypes().forEach(metricRecorder::recordPrivacyOutputMasked);
        }
        sendMaskedChunks(emitter, traceId, model, privacy.content(), disconnected);
        synchronized (pending) { pending.setLength(0); }
        return true;
    }

    private void sendMaskedChunks(SseEmitter emitter, String traceId, String model, String text, AtomicBoolean disconnected) {
        int maxChars = Math.max(1, chunkSize) * CHARS_PER_TOKEN;
        for (int start = 0; start < text.length() && !disconnected.get();) {
            int end = Math.min(text.length(), start + maxChars);
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) {
                end--;
            }
            send(emitter, "token", AiStreamEvent.token(traceId, model, text.substring(start, end)));
            start = end;
        }
    }

    private AiStreamEvent costEvent(AiStreamEvent event, AiCostControlService.CostReservation reservation, LlmStreamControl control) {
        if (reservation == null) return event;
        var usage = control.snapshot().usage();
        boolean reported = reservation.state() == AiCostControlService.ReservationState.SETTLED_REPORTED;
        String source = switch (reservation.state()) {
            case SETTLED_REPORTED -> "REPORTED";
            case SETTLED_ESTIMATED -> "ESTIMATED";
            case RELEASED -> "RELEASED";
            default -> "PENDING";
        };
        return event.withCost(reported && usage != null ? usage.promptTokens() : null,
                reported && usage != null ? usage.completionTokens() : null, reported, source);
    }

    private void sendTerminal(SseEmitter emitter, String event, AiStreamEvent data) {
        try {
            send(emitter, event, data);
            emitter.complete();
        } catch (RuntimeException exception) {
            emitter.completeWithError(exception);
        }
    }

    private void send(SseEmitter emitter, String event, AiStreamEvent data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException exception) {
            emitter.completeWithError(exception);
            throw new IllegalStateException("SSE delivery failed", exception);
        }
    }
}
