package com.ohgiraffers.handlermethod.api;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.ohgiraffers.handlermethod.guardrail.InputGuardrailClient;
import com.ohgiraffers.handlermethod.guardrail.InputGuardrailResult;
import com.ohgiraffers.handlermethod.guardrail.OutputGuardrailClient;
import com.ohgiraffers.handlermethod.guardrail.OutputGuardrailResult;
import com.ohgiraffers.handlermethod.llm.LlmClient;
import com.ohgiraffers.handlermethod.llm.LlmStreamControl;
import com.ohgiraffers.handlermethod.llm.StreamingLlmClient;
import com.ohgiraffers.handlermethod.security.JwtTokenProvider;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression for cost-control experiment A7 (2026-10-25): with a session budget that covers one request but not two,
 * the second request used to pass the input check, pay for the answer, and only then be stopped at the output check
 * (generation cost spent, nothing delivered). The whole-request precheck must stop it before any paid stage.
 *
 * Numbers for message "민사", 64 output tokens, guard estimates 256/512, mocks without guard usage (estimated):
 * plan per request ≈ $0.000823; first request spends ≈ $0.000699; session budget $0.0012 fits one plan, not two.
 * Without the precheck the second request reaches the LLM (input $0.001045, answer $0.001119 ≤ 0.0012) and is
 * blocked only at the output check ($0.001398 > 0.0012), so this test fails on the old code.
 */
@SpringBootTest(properties = {"spring.jpa.show-sql=false", "llm.max-output-tokens=64",
        "llm.cost.session-budget-usd=0.0012"})
@AutoConfigureMockMvc
class RequestBudgetPrecheckIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired MeterRegistry registry;
    @MockitoBean InputGuardrailClient inputGuard;
    @MockitoBean OutputGuardrailClient outputGuard;
    @MockitoBean LlmClient llm;
    @MockitoBean StreamingLlmClient streamingLlm;

    @BeforeEach
    void setUp() {
        when(inputGuard.check(anyString())).thenAnswer(call ->
                new InputGuardrailResult("PASSED", call.getArgument(0), "INPUT"));
        when(outputGuard.check(anyString())).thenAnswer(call ->
                new OutputGuardrailResult("PASSED", call.getArgument(0), "OUTPUT"));
        when(llm.chat(anyString(), anyString())).thenReturn(
                new LlmClient.LlmResponse("전자소송 안내", "test-model", 10, 4));
        doAnswer(call -> {
            call.<LlmStreamControl>getArgument(5).dispatch(() -> {});
            Consumer<String> chunk = call.getArgument(2);
            Consumer<StreamingLlmClient.Usage> complete = call.getArgument(3);
            chunk.accept("전자소송 안내");
            complete.accept(new StreamingLlmClient.Usage("test-model", 10, 4));
            return null;
        }).when(streamingLlm).stream(anyString(), anyString(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/ai/chat", "/api/ai/chat/stream"})
    void secondRequestIsStoppedBeforeAnyPaidStageInsteadOfAfterTheAnswer(String path) throws Exception {
        String sessionId = "precheck-" + UUID.randomUUID();
        double precheckBlocksBefore = precheckBlocks();

        DocumentContext first = reply(path, sessionId);
        assertThat(first.read("$.blocked", Boolean.class)).isFalse();
        assertThat(first.read("$.fallback", Boolean.class)).isFalse();

        DocumentContext second = reply(path, sessionId);
        assertThat(second.read("$.blocked", Boolean.class)).isTrue();
        assertThat(second.read("$.reasonCode", String.class)).isEqualTo("COST_LIMIT_BLOCKED");
        assertThat(second.read("$.detailCode", String.class)).isEqualTo("SESSION_BUDGET_EXCEEDED");

        // Only the first request reached any paid client; the second was stopped by the precheck.
        verify(inputGuard, times(1)).check(anyString());
        verify(outputGuard, times(1)).check(anyString());
        if (path.endsWith("/stream")) {
            verify(streamingLlm, times(1)).stream(anyString(), anyString(), any(), any(), any(), any());
        } else {
            verify(llm, times(1)).chat(anyString(), anyString());
        }
        assertThat(precheckBlocks() - precheckBlocksBefore).isEqualTo(1.0);
        assertThat(registry.get("ai.cost.reserved.usd").gauge().value()).isZero();
    }

    private double precheckBlocks() {
        return registry.find("ai.cost.blocked").tag("stage", "request_precheck").counters().stream()
                .mapToDouble(Counter::count).sum();
    }

    private DocumentContext reply(String path, String sessionId) throws Exception {
        MvcResult result = mvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.createToken("user", "ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"민사\",\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk()).andReturn();
        if (path.endsWith("/stream")) {
            assertThat(result.getRequest().isAsyncStarted()).isTrue();
            result.getAsyncResult(5_000);
            result = mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn();
            String lastData = result.getResponse().getContentAsString(StandardCharsets.UTF_8).lines()
                    .filter(line -> line.startsWith("data:")).reduce((previous, next) -> next).orElseThrow();
            return JsonPath.parse(lastData.substring("data:".length()));
        }
        return JsonPath.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
