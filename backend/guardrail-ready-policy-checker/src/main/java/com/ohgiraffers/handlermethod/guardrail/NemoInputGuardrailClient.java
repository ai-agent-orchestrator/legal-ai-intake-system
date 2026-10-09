package com.ohgiraffers.handlermethod.guardrail;

import org.springframework.beans.factory.annotation.Value;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class NemoInputGuardrailClient implements InputGuardrailClient {

    private final RestClient restClient;
    private final MeterRegistry meterRegistry;

    public NemoInputGuardrailClient(
            RestClient.Builder builder,
            @Value("${guardrails.base-url}") String baseUrl,
            MeterRegistry meterRegistry
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.meterRegistry = meterRegistry;
    }

    @Override
    public InputGuardrailResult check(String message) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            NemoCheckResponse response = restClient.post()
                    .uri("/v1/input-rails/check")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new NemoCheckRequest(List.of(new NemoMessage("user", message))))
                    .retrieve()
                    .body(NemoCheckResponse.class);

            if (response == null) {
                throw new IllegalStateException("NeMo Guardrails returned an empty response");
            }

            Counter.builder("ai.input.rail.decisions")
                    .tag("status", response.status())
                    .tag("rail", response.rail() == null ? "none" : response.rail())
                    .register(meterRegistry)
                    .increment();
            return new InputGuardrailResult(response.status(), response.content(), response.rail());
        } catch (RuntimeException exception) {
            Counter.builder("ai.input.rail.errors")
                    .tag("type", exception.getClass().getSimpleName())
                    .register(meterRegistry)
                    .increment();
            throw exception;
        } finally {
            sample.stop(Timer.builder("ai.input.rail.duration")
                    .description("NeMo input rail request duration")
                    .register(meterRegistry));
        }
    }

    private record NemoCheckRequest(List<NemoMessage> messages) {
    }

    private record NemoMessage(String role, String content) {
    }

    private record NemoCheckResponse(String status, String content, String rail) {
    }
}
