package com.ohgiraffers.handlermethod.cost;

import com.ohgiraffers.handlermethod.guardrail.InputGuardrailClient;
import com.ohgiraffers.handlermethod.guardrail.InputGuardrailResult;
import com.ohgiraffers.handlermethod.guardrail.OutputGuardrailClient;
import com.ohgiraffers.handlermethod.guardrail.OutputGuardrailResult;
import com.ohgiraffers.handlermethod.guardrail.OutputGuardrailPolicy;
import com.ohgiraffers.handlermethod.guardrail.GuardrailUsage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
public class CostedGuardrailService {
    private final InputGuardrailClient input;
    private final OutputGuardrailClient output;
    private final AiCostControlService cost;
    private final String model;
    private final BigDecimal inputRate;
    private final BigDecimal outputRate;
    private final int promptOverheadTokens;
    private final int estimatedOutputTokens;

    public CostedGuardrailService(InputGuardrailClient input, OutputGuardrailClient output,
            AiCostControlService cost,
            @Value("${guardrails.cost.model:gpt-4o-mini}") String model,
            @Value("${guardrails.cost.input-usd-per-1k-tokens:0.00015}") BigDecimal inputRate,
            @Value("${guardrails.cost.output-usd-per-1k-tokens:0.00060}") BigDecimal outputRate,
            @Value("${guardrails.cost.prompt-overhead-tokens:256}") int promptOverheadTokens,
            @Value("${guardrails.cost.estimated-output-tokens:512}") int estimatedOutputTokens) {
        if (inputRate.signum() < 0 || outputRate.signum() < 0
                || promptOverheadTokens < 0 || estimatedOutputTokens < 1) {
            throw new IllegalArgumentException("Invalid Guardrail cost configuration");
        }
        this.input = input;
        this.output = output;
        this.cost = cost;
        this.model = model;
        this.inputRate = inputRate;
        this.outputRate = outputRate;
        this.promptOverheadTokens = promptOverheadTokens;
        this.estimatedOutputTokens = estimatedOutputTokens;
    }

    /**
     * Upper estimate of UTF-8 bytes per generated answer token, used only to size the output check before the answer
     * exists. Korean averages roughly 3-5 bytes per token; actual reservations use the real answer text.
     */
    static final int ANSWER_BYTES_PER_TOKEN = 6;

    /** Estimate for the input check of {@code message} (same basis as the reservation in checkInput). */
    public AiCostControlService.StageEstimate inputCheckEstimate(String message) {
        return new AiCostControlService.StageEstimate(AiCostControlService.CostStage.INPUT_GUARDRAIL,
                AiCostControlService.estimateTokens(message) + promptOverheadTokens, estimatedOutputTokens,
                inputRate, outputRate);
    }

    /** Estimate for the output check of an answer that is at most {@code maxAnswerTokens} long. */
    public AiCostControlService.StageEstimate outputCheckEstimate(int maxAnswerTokens) {
        int answerTokens = Math.multiplyExact(Math.max(maxAnswerTokens, 0), ANSWER_BYTES_PER_TOKEN);
        return new AiCostControlService.StageEstimate(AiCostControlService.CostStage.OUTPUT_GUARDRAIL,
                Math.addExact(answerTokens, promptOverheadTokens), estimatedOutputTokens, inputRate, outputRate);
    }

    /** Every paid stage of one chat request (input check, answer, output check) for the whole-request precheck. */
    public List<AiCostControlService.StageEstimate> chatPlan(String systemPrompt, String message) {
        return List.of(inputCheckEstimate(message), cost.answerEstimate(systemPrompt, message),
                outputCheckEstimate(cost.maxOutputTokens()));
    }

    public Check<InputGuardrailResult> checkInput(String message, String sessionId) {
        return check(AiCostControlService.CostStage.INPUT_GUARDRAIL, message, sessionId,
                () -> input.check(message), InputGuardrailResult::usage);
    }

    public Check<OutputGuardrailResult> checkOutput(String response, String sessionId) {
        OutputGuardrailResult local = OutputGuardrailPolicy.localBlock(response);
        if (local != null) {
            return new Check<>(local, true, AiCostControlService.CostSource.REPORTED.name());
        }
        return check(AiCostControlService.CostStage.OUTPUT_GUARDRAIL, response, sessionId,
                () -> output.check(response), OutputGuardrailResult::usage);
    }

    private <T> Check<T> check(AiCostControlService.CostStage stage, String text, String sessionId,
            Supplier<T> call, Function<T, GuardrailUsage> usageOf) {
        var decision = cost.reserveStageWithBlock(stage,
                AiCostControlService.estimateTokens(text) + promptOverheadTokens,
                estimatedOutputTokens, model, sessionId, inputRate, outputRate);
        if (!decision.allowed()) {
            throw new CostLimitException(decision.block());
        }
        var reservation = decision.reservation();
        try {
            T result = call.get();
            GuardrailUsage usage = usageOf.apply(result);
            boolean available = usage != null && usage.available();
            var source = cost.recordUsage(reservation, available ? usage.prompt_tokens().intValue() : 0,
                    available ? usage.completion_tokens().intValue() : 0, available);
            return new Check<>(result, available, source.name());
        } catch (RuntimeException exception) {
            // A timeout/remote failure does not establish that the provider charged zero.
            cost.recordUsage(reservation, 0, 0, false);
            throw exception;
        }
    }

    public record Check<T>(T result, boolean usageAvailable, String costSource) { }
}
