package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.AiChatRequest;
import com.ohgiraffers.handlermethod.dto.AiChatResponse;
import com.ohgiraffers.handlermethod.cost.AiCostControlService;
import com.ohgiraffers.handlermethod.cost.CostedGuardrailService;
import com.ohgiraffers.handlermethod.cost.CostLimitException;
import com.ohgiraffers.handlermethod.guardrail.InputGuardrailResult;
import com.ohgiraffers.handlermethod.guardrail.OutputGuardrailResult;
import com.ohgiraffers.handlermethod.llm.LlmClient;
import com.ohgiraffers.handlermethod.riskengine.DynamicGuardDecision;
import com.ohgiraffers.handlermethod.riskengine.FixedGuardResult;
import com.ohgiraffers.handlermethod.riskengine.LegalDynamicGuardService;
import com.ohgiraffers.handlermethod.riskengine.LegalFixedGuardService;
import com.ohgiraffers.handlermethod.riskengine.PrivacyOutputGuardResult;
import com.ohgiraffers.handlermethod.riskengine.PrivacyOutputGuardService;
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

    private final CostedGuardrailService guardrails;
    private final LlmClient llmClient;
    private final AiMetricRecorder metricRecorder;
    private final AiCostControlService costControlService;
    private final LegalFixedGuardService legalFixedGuardService;
    private final LegalDynamicGuardService legalDynamicGuardService;
    private final PrivacyOutputGuardService privacyOutputGuardService;
    private final String systemPrompt;
    private final String configuredModel;

    public AiService(
            CostedGuardrailService guardrails,
            LlmClient llmClient,
            AiMetricRecorder metricRecorder,
            AiCostControlService costControlService,
            LegalFixedGuardService legalFixedGuardService,
            LegalDynamicGuardService legalDynamicGuardService,
            PrivacyOutputGuardService privacyOutputGuardService,
            @Value("${llm.system-prompt}") String systemPrompt,
            @Value("${llm.model}") String configuredModel
    ) {
        this.guardrails = guardrails;
        this.llmClient = llmClient;
        this.metricRecorder = metricRecorder;
        this.costControlService = costControlService;
        this.legalFixedGuardService = legalFixedGuardService;
        this.legalDynamicGuardService = legalDynamicGuardService;
        this.privacyOutputGuardService = privacyOutputGuardService;
        this.systemPrompt = systemPrompt;
        this.configuredModel = configuredModel;
    }

    public AiChatResponse chat(AiChatRequest request) {
        String traceId = TraceContext.currentTraceId();
        Timer.Sample timer = metricRecorder.start();

        try {
            DynamicGuardDecision preCheck = legalDynamicGuardService.preCheck(request.sessionId());
            if (preCheck.blocked()) {
                metricRecorder.recordRequest("dynamic_guard_blocked", "none");
                metricRecorder.recordDynamicBlock(preCheck.reasonCode());
                metricRecorder.recordLlmCallPrevented(preCheck.reasonCode());
                return AiChatResponse.fallback(
                        preCheck.safeResponse(), traceId, "none", preCheck.reasonCode());
            }

            FixedGuardResult fixedGuard = legalFixedGuardService.check(request.message());
            if (!fixedGuard.allowed()) {
                metricRecorder.recordRequest("fixed_guard_blocked", "none");
                metricRecorder.recordLegalGuardrailBlock(fixedGuard.reasonCode());
                metricRecorder.recordLlmCallPrevented(fixedGuard.reasonCode());
                DynamicGuardDecision dynamicDecision = legalDynamicGuardService.recordBlockedEvent(
                        request.sessionId(), fixedGuard.reasonCode());
                if (dynamicDecision.blocked()) {
                    metricRecorder.recordRequest("dynamic_guard_blocked", "none");
                    metricRecorder.recordDynamicBlock(dynamicDecision.reasonCode());
                    return AiChatResponse.fallback(
                            dynamicDecision.safeResponse(), traceId, "none", dynamicDecision.reasonCode(),
                            dynamicDecision.reasonCode(), fixedGuard.reasonCode());
                }
                return AiChatResponse.fallback(
                        fixedGuard.safeResponse(), traceId, "none", fixedGuard.reasonCode());
            }

            var pageLimitBlock = costControlService.checkInputPageLimit(request.message());
            if (pageLimitBlock.isPresent()) {
                AiCostControlService.CostLimitBlock block = pageLimitBlock.get();
                metricRecorder.recordRequest("cost_blocked", configuredModel);
                metricRecorder.recordLegalCostBlock(block.detailCode());
                metricRecorder.recordLlmCallPrevented(block.reasonCode());
                DynamicGuardDecision dynamicDecision = legalDynamicGuardService.recordBlockedEvent(
                        request.sessionId(), block.detailCode());
                if (dynamicDecision.blocked()) {
                    metricRecorder.recordRequest("dynamic_guard_blocked", configuredModel);
                    metricRecorder.recordDynamicBlock(dynamicDecision.reasonCode());
                    return AiChatResponse.fallback(
                            dynamicDecision.safeResponse(), traceId, configuredModel, dynamicDecision.reasonCode(),
                            dynamicDecision.reasonCode(), block.detailCode());
                }
                return AiChatResponse.fallback(
                        block.safeResponse(), traceId, configuredModel, block.reasonCode(),
                        block.reasonCode(), block.detailCode());
            }

            InputGuardrailResult input = checkInput(request.message(), request.sessionId());
            if (!input.allowed()) {
                metricRecorder.recordRequest("input_blocked", "none");
                metricRecorder.recordLegalGuardrailBlock("NEMO_INPUT_GUARDRAIL");
                metricRecorder.recordLlmCallPrevented("INPUT_GUARDRAIL_BLOCKED");
                DynamicGuardDecision dynamicDecision = legalDynamicGuardService.recordBlockedEvent(
                        request.sessionId(), "NEMO_INPUT_GUARDRAIL");
                if (dynamicDecision.blocked()) {
                    metricRecorder.recordRequest("dynamic_guard_blocked", "none");
                    metricRecorder.recordDynamicBlock(dynamicDecision.reasonCode());
                    return AiChatResponse.fallback(
                            dynamicDecision.safeResponse(), traceId, "none", dynamicDecision.reasonCode(),
                            dynamicDecision.reasonCode(), "NEMO_INPUT_GUARDRAIL");
                }
                return AiChatResponse.fallback(
                        input.content(), traceId, "none", "INPUT_GUARDRAIL_BLOCKED",
                        "INPUT_GUARDRAIL_BLOCKED", "NEMO_INPUT_GUARDRAIL");
            }

            var reservationDecision = costControlService
                    .reserveWithBlock(systemPrompt, input.content(), configuredModel, request.sessionId());
            if (!reservationDecision.allowed()) {
                metricRecorder.recordRequest("cost_blocked", configuredModel);
                AiCostControlService.CostLimitBlock block = reservationDecision.block();
                metricRecorder.recordLegalCostBlock(block.detailCode());
                metricRecorder.recordLlmCallPrevented(block.reasonCode());
                return AiChatResponse.fallback(
                        block.safeResponse(),
                        traceId,
                        configuredModel,
                        block.detailCode(), block.reasonCode(), block.detailCode());
            }

            AiCostControlService.CostReservation reservation = reservationDecision.reservation();
            LlmClient.LlmResponse llm;
            AiCostControlService.CostSource costSource;
            try {
                llm = callLlm(input.content());
                costSource = costControlService.recordUsage(
                        reservation, llm.promptTokens(), llm.completionTokens(), llm.usageAvailable());
            } catch (RuntimeException exception) {
                costControlService.recordUsage(reservation, 0, 0, false);
                throw exception;
            }
            if (llm.usageAvailable()) {
                metricRecorder.recordTokens(llm.model(), llm.promptTokens(), llm.completionTokens());
            }

            OutputGuardrailResult output = checkOutput(llm.content(), request.sessionId());
            if (!output.allowed()) {
                metricRecorder.recordRequest("output_blocked", llm.model());
                return AiChatResponse.fallback(
                        OUTPUT_POLICY_FALLBACK_MESSAGE,
                        traceId,
                        llm.model(),
                        "OUTPUT_POLICY_BLOCKED");
            }

            metricRecorder.recordRequest("success", llm.model());
            PrivacyOutputGuardResult privacyOutput = privacyOutputGuardService.mask(output.content());
            if (privacyOutput.masked()) {
                privacyOutput.piiTypes().forEach(metricRecorder::recordPrivacyOutputMasked);
            }
            return AiChatResponse.success(privacyOutput.content(), traceId, llm.model(),
                    llm.usageAvailable(), costSource.name());
        } catch (CostLimitException exception) {
            var block = exception.block();
            metricRecorder.recordRequest("cost_blocked", configuredModel);
            metricRecorder.recordLegalCostBlock(block.detailCode());
            return AiChatResponse.fallback(block.safeResponse(), traceId, configuredModel,
                    block.detailCode(), block.reasonCode(), block.detailCode());
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

    private InputGuardrailResult checkInput(String message, String sessionId) {
        Timer.Sample timer = metricRecorder.start();
        try {
            InputGuardrailResult result = guardrails.checkInput(message, sessionId).result();
            metricRecorder.recordStage("input_guardrail", result.status());
            return result;
        } catch (CostLimitException exception) {
            throw exception;
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

    private OutputGuardrailResult checkOutput(String answer, String sessionId) {
        Timer.Sample timer = metricRecorder.start();
        try {
            OutputGuardrailResult result = guardrails.checkOutput(answer, sessionId).result();
            metricRecorder.recordStage("output_guardrail", result.status());
            return result;
        } catch (CostLimitException exception) {
            throw exception;
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
