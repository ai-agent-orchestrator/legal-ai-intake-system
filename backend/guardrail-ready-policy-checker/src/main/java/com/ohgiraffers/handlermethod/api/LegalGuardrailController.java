package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.guardrail.InputGuardrailResult;
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
public class LegalGuardrailController {

    private final CostedGuardrailService guardrails;

    public LegalGuardrailController(CostedGuardrailService guardrails) {
        this.guardrails = guardrails;
    }

    @PostMapping("/input")
    public ResponseEntity<InputCheckResponse> checkInput(
            @Valid @RequestBody InputCheckRequest request
    ) {
        try {
            var checked = guardrails.checkInput(request.message(), request.sessionId());
            InputGuardrailResult result = checked.result();
            return ResponseEntity.ok(new InputCheckResponse(
                    result.status(),
                    result.allowed(),
                    result.content(),
                    result.rail(),
                    TraceContext.currentTraceId(), null, null, checked.usageAvailable(), checked.costSource()
            ));
        } catch (CostLimitException exception) {
            var block = exception.block();
            return ResponseEntity.ok(new InputCheckResponse("BLOCKED", false, block.safeResponse(),
                    "cost control", TraceContext.currentTraceId(), block.reasonCode(), block.detailCode(), null, null));
        }
    }

    public record InputCheckRequest(@NotBlank String message, String sessionId) {
    }

    public record InputCheckResponse(
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
