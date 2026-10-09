package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.guardrail.OutputGuardrailClient;
import com.ohgiraffers.handlermethod.guardrail.OutputGuardrailResult;
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

    private final OutputGuardrailClient outputGuardrailClient;

    public LegalOutputGuardrailController(OutputGuardrailClient outputGuardrailClient) {
        this.outputGuardrailClient = outputGuardrailClient;
    }

    @PostMapping("/output")
    public ResponseEntity<OutputCheckResponse> checkOutput(
            @Valid @RequestBody OutputCheckRequest request
    ) {
        OutputGuardrailResult result = outputGuardrailClient.check(request.response());

        return ResponseEntity.ok(new OutputCheckResponse(
                result.status(),
                result.allowed(),
                result.content(),
                result.rail(),
                TraceContext.currentTraceId()
        ));
    }

    public record OutputCheckRequest(@NotBlank String response) {
    }

    public record OutputCheckResponse(
            String status,
            boolean allowed,
            String content,
            String rail,
            String traceId
    ) {
    }
}
