package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.AiChatRequest;
import com.ohgiraffers.handlermethod.dto.AiChatResponse;
import com.ohgiraffers.handlermethod.cost.AiCostControlService;
import com.ohgiraffers.handlermethod.guardrail.InputGuardrailClient;
import com.ohgiraffers.handlermethod.guardrail.InputGuardrailResult;
import com.ohgiraffers.handlermethod.guardrail.OutputGuardrailClient;
import com.ohgiraffers.handlermethod.guardrail.OutputGuardrailResult;
import com.ohgiraffers.handlermethod.llm.LlmClient;
import com.ohgiraffers.handlermethod.support.AiMetricRecorder;
import com.ohgiraffers.handlermethod.support.TraceContext;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.Timer;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private static final String FALLBACK_MESSAGE =
            "현재 요청을 안전하게 처리할 수 없습니다. 잠시 후 다시 시도하거나 전문가에게 상담받아 주세요.";
    private static final String OUTPUT_POLICY_FALLBACK_MESSAGE =
            "해당 답변은 제공할 수 없습니다. 일반적인 법률 정보만 안내하며, 구체적인 사건은 전문가 상담이 필요합니다.";

    private final InputGuardrailClient inputGuardrailClient;
    private final OutputGuardrailClient outputGuardrailClient;
    private final LlmClient llmClient;
    private final AiMetricRecorder metricRecorder;
    private final AiCostControlService costControlService;
    private final String systemPrompt;
    private final String configuredModel;

    public AiService(
            InputGuardrailClient inputGuardrailClient,
            OutputGuardrailClient outputGuardrailClient,
            LlmClient llmClient,
            AiMetricRecorder metricRecorder,
            AiCostControlService costControlService,
            @Value("${llm.system-prompt}") String systemPrompt,
            @Value("${llm.model}") String configuredModel
    ) {
        this.inputGuardrailClient = inputGuardrailClient;
        this.outputGuardrailClient = outputGuardrailClient;
        this.llmClient = llmClient;
        this.metricRecorder = metricRecorder;
        this.costControlService = costControlService;
        this.systemPrompt = systemPrompt;
        this.configuredModel = configuredModel;
    }

    public AiChatResponse chat(AiChatRequest request) {
        String traceId = TraceContext.currentTraceId();
        Timer.Sample timer = metricRecorder.start();

        try {
            InputGuardrailResult input = checkInput(request.message());
            if (!input.allowed()) {
                metricRecorder.recordRequest("input_blocked", "none");
                return AiChatResponse.fallback(
                        input.content(), traceId, "none", "INPUT_GUARDRAIL_BLOCKED");
            }

            var reservation = costControlService
                    .reserve(systemPrompt, input.content(), configuredModel);
            if (reservation.isEmpty()) {
                metricRecorder.recordRequest("cost_blocked", configuredModel);
                return AiChatResponse.technicalFallback(
                        "사용량 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.",
                        traceId,
                        configuredModel,
                        "COST_LIMIT_EXCEEDED");
            }

            LlmClient.LlmResponse llm;
            try {
                llm = callLlm(input.content());
                costControlService.recordActualUsage(
                        reservation.get(), llm.promptTokens(), llm.completionTokens());
            } catch (RuntimeException exception) {
                costControlService.release(reservation.get(), "LLM_FAILURE");
                throw exception;
            }
            metricRecorder.recordTokens(llm.model(), llm.promptTokens(), llm.completionTokens());

            OutputGuardrailResult output = checkOutput(llm.content());
            if (!output.allowed()) {
                metricRecorder.recordRequest("output_blocked", llm.model());
                return AiChatResponse.fallback(
                        OUTPUT_POLICY_FALLBACK_MESSAGE,
                        traceId,
                        llm.model(),
                        "OUTPUT_POLICY_BLOCKED");
            }

            metricRecorder.recordRequest("success", llm.model());
            return AiChatResponse.success(output.content(), traceId, llm.model());
        } catch (PipelineFailure failure) {
            metricRecorder.recordRequest("fallback", "unknown");
            log.warn("AI chat fallback traceId={} stage={} reason={}",
                    traceId, failure.stage(), failure.reason());
            return AiChatResponse.technicalFallback(
                    FALLBACK_MESSAGE, traceId, "unknown", failure.reason());
        } finally {
            metricRecorder.stop(timer);
        }
    }

    private InputGuardrailResult checkInput(String message) {
        Timer.Sample timer = metricRecorder.start();
        try {
            InputGuardrailResult result = inputGuardrailClient.check(message);
            metricRecorder.recordStage("input_guardrail", result.status());
            return result;
        } catch (RuntimeException exception) {
            metricRecorder.recordFailure("input_guardrail", exception.getClass().getSimpleName());
            throw new PipelineFailure("input_guardrail", "INPUT_GUARDRAIL_UNAVAILABLE", exception);
        } finally {
            metricRecorder.stopStage(timer, "input_guardrail");
        }
    }

    private LlmClient.LlmResponse callLlm(String userMessage) {
        Timer.Sample timer = metricRecorder.start();
        try {
            LlmClient.LlmResponse response = llmClient.chat(systemPrompt, userMessage);
            metricRecorder.recordStage("llm", "success");
            return response;
        } catch (RuntimeException exception) {
            metricRecorder.recordFailure("llm", exception.getClass().getSimpleName());
            throw new PipelineFailure("llm", "LLM_UNAVAILABLE", exception);
        } finally {
            metricRecorder.stopStage(timer, "llm");
        }
    }

    private OutputGuardrailResult checkOutput(String answer) {
        Timer.Sample timer = metricRecorder.start();
        try {
            OutputGuardrailResult result = outputGuardrailClient.check(answer);
            metricRecorder.recordStage("output_guardrail", result.status());
            return result;
        } catch (RuntimeException exception) {
            metricRecorder.recordFailure("output_guardrail", exception.getClass().getSimpleName());
            throw new PipelineFailure("output_guardrail", "OUTPUT_GUARDRAIL_UNAVAILABLE", exception);
        } finally {
            metricRecorder.stopStage(timer, "output_guardrail");
        }
    }

    private static final class PipelineFailure extends RuntimeException {

        private final String stage;
        private final String reason;

        private PipelineFailure(String stage, String reason, Throwable cause) {
            super(cause);
            this.stage = stage;
            this.reason = reason;
        }

        private String stage() {
            return stage;
        }

        private String reason() {
            return reason;
        }
    }
}
