package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.guardrail.OutputGuardrailResult;
import com.ohgiraffers.handlermethod.cost.CostedGuardrailService;
import com.ohgiraffers.handlermethod.cost.CostLimitException;
import com.ohgiraffers.handlermethod.support.TraceContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/legal/guardrail")
public class LegalOutputGuardrailController {

    private final CostedGuardrailService guardrails;

    public LegalOutputGuardrailController(CostedGuardrailService guardrails) {
        this.guardrails = guardrails;
    }

    @PostMapping("/output")
    public ResponseEntity<OutputCheckResponse> checkOutput(
            @Valid @RequestBody OutputCheckRequest request
    ) {
        try {
            var checked = guardrails.checkOutput(request.response(), request.sessionId());
            OutputGuardrailResult result = checked.result();
            return ResponseEntity.ok(new OutputCheckResponse(
                    result.status(),
                    result.allowed(),
                    result.content(),
                    result.rail(),
                    TraceContext.currentTraceId(), null, null, checked.usageAvailable(), checked.costSource()
            ));
        } catch (CostLimitException exception) {
            var block = exception.block();
            return ResponseEntity.ok(new OutputCheckResponse("BLOCKED", false, block.safeResponse(),
                    "cost control", TraceContext.currentTraceId(), block.reasonCode(), block.detailCode(), null, null));
        }
    }

    public record OutputCheckRequest(@NotBlank String response, String sessionId) {
    }

    public record OutputCheckResponse(
            String status,
            boolean allowed,
            String content,
            String rail,
            String traceId,
            String reasonCode,
            String detailCode,
            Boolean usageAvailable,
            String costSource
    ) {
    }
}
