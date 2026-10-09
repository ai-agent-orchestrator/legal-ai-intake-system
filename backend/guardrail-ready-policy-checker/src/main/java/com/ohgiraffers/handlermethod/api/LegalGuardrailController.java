package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.guardrail.InputGuardrailClient;
import com.ohgiraffers.handlermethod.guardrail.InputGuardrailResult;
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

    private final InputGuardrailClient inputGuardrailClient;

    public LegalGuardrailController(InputGuardrailClient inputGuardrailClient) {
        this.inputGuardrailClient = inputGuardrailClient;
    }

    @PostMapping("/input")
    public ResponseEntity<InputCheckResponse> checkInput(
            @Valid @RequestBody InputCheckRequest request
    ) {
        InputGuardrailResult result = inputGuardrailClient.check(request.message());

        return ResponseEntity.ok(new InputCheckResponse(
                result.status(),
                result.allowed(),
                result.content(),
                result.rail(),
                TraceContext.currentTraceId()
        ));
    }

    public record InputCheckRequest(@NotBlank String message) {
    }

    public record InputCheckResponse(
            String status,
            boolean allowed,
            String content,
            String rail,
            String traceId
    ) {
    }
}
