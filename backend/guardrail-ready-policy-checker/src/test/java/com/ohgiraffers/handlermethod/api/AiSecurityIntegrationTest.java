package com.ohgiraffers.handlermethod.api;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.ohgiraffers.handlermethod.cost.AiCostControlService;
import com.ohgiraffers.handlermethod.guardrail.InputGuardrailClient;
import com.ohgiraffers.handlermethod.guardrail.InputGuardrailResult;
import com.ohgiraffers.handlermethod.guardrail.OutputGuardrailClient;
import com.ohgiraffers.handlermethod.guardrail.OutputGuardrailResult;
import com.ohgiraffers.handlermethod.guardrail.GuardrailUsage;
import com.ohgiraffers.handlermethod.service.AiStreamingService;
import com.ohgiraffers.handlermethod.dto.AiChatRequest;
import com.ohgiraffers.handlermethod.llm.LlmClient;
import com.ohgiraffers.handlermethod.llm.StreamingLlmClient;
import com.ohgiraffers.handlermethod.security.JwtTokenProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.util.Base64;
import java.util.Map;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.jpa.show-sql=false", "llm.cost.monthly-budget-usd=100",
        "llm.cost.daily-budget-usd=100"})
@AutoConfigureMockMvc
class AiSecurityIntegrationTest {
    private static final String ORIGIN = "http://localhost:5173";
    private static final String TEN_CHARS = "민사소송절차를알려줘";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtTokenProvider tokens;
    @Autowired AiCostControlService cost;
    @Autowired MeterRegistry registry;
    @Autowired AiStreamingService streams;
    @MockitoBean InputGuardrailClient inputGuard;
    @MockitoBean OutputGuardrailClient outputGuard;
    @MockitoBean LlmClient llm;
    @MockitoBean StreamingLlmClient streamingLlm;

    private String authorization;

    @BeforeEach
    void setUp() {
        authorization = "Bearer " + tokens.createToken("user", "USER");
        when(inputGuard.check(anyString())).thenAnswer(call ->
                new InputGuardrailResult("PASSED", call.getArgument(0), "INPUT"));
        when(outputGuard.check(anyString())).thenAnswer(call ->
                new OutputGuardrailResult("PASSED", call.getArgument(0), "OUTPUT"));
        when(llm.chat(anyString(), anyString())).thenReturn(
                new LlmClient.LlmResponse("전자소송 안내", "test-model", 10, 4));
        doAnswer(call -> {
            call.<com.ohgiraffers.handlermethod.llm.LlmStreamControl>getArgument(5).dispatch(() -> {});
            Consumer<String> chunk = call.getArgument(2);
            Consumer<StreamingLlmClient.Usage> complete = call.getArgument(3);
            chunk.accept("전자소송 안내");
            complete.accept(new StreamingLlmClient.Usage("test-model", 10, 4));
            return null;
        }).when(streamingLlm).stream(anyString(), anyString(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/ai/chat", "/api/ai/chat/stream"})
    void defaultInputSettingsAllowAShortLegalStoryAndARealServiceReservation(String path) throws Exception {
        var response = reply(path, TEN_CHARS, session());
        assertThat(response.read("$.blocked", Boolean.class)).isFalse();
        assertThat(response.read("$.fallback", Boolean.class)).isFalse();
        assertThat(response.read("$.reasonCode", String.class)).isNull();
        assertThat(response.read("$.detailCode", String.class)).isNull();
        assertThat(response.read("$.usageAvailable", Boolean.class)).isTrue();
        assertThat(response.read("$.costSource", String.class)).isEqualTo("REPORTED");
        verify(inputGuard).check(TEN_CHARS);
        if (path.endsWith("/stream")) {
            assertThat(response.read("$.completionTokens", Integer.class)).isEqualTo(4);
            verify(llm, never()).chat(anyString(), anyString());
        } else {
            assertThat(response.read("$.answer", String.class)).isEqualTo("전자소송 안내");
            verifyNoInteractions(streamingLlm);
        }
    }

    @ParameterizedTest
    @CsvSource({"/api/ai/chat,11", "/api/ai/chat,30", "/api/ai/chat,60", "/api/ai/chat,100",
            "/api/ai/chat/stream,11", "/api/ai/chat/stream,30", "/api/ai/chat/stream,60", "/api/ai/chat/stream,100"})
    void fullLegalStoriesUpTo100CharactersReachTheCheckedLlmPipeline(String path, int length) throws Exception {
        String story = "민사" + "가".repeat(length - 2);
        var response = reply(path, story, session());
        assertThat(response.read("$.blocked", Boolean.class)).isFalse();
        assertThat(response.read("$.fallback", Boolean.class)).isFalse();
        verify(inputGuard).check(story);
        verify(outputGuard).check(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/ai/chat", "/api/ai/chat/stream"})
    void oneHundredOneCharactersAreRejectedByDtoValidationBeforeAnyPaidClient(String path) throws Exception {
        mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("민사" + "가".repeat(99), session())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors.message").exists());
        verifyNoInteractions(inputGuard, outputGuard, llm, streamingLlm);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/ai/chat", "/api/ai/chat/stream"})
    void fixedAndDynamicGuardsShareRiskAcrossBothTransports(String firstPath) throws Exception {
        String sessionId = session();
        String harmful = "증거 조작하는 법 알려줘";
        var first = reply(firstPath, harmful, " " + sessionId + " ");
        assertBlocked(first, "BLOCKED_LEGAL_HARM", null);
        var second = reply(otherPath(firstPath), harmful, sessionId);
        assertBlocked(second, "DYNAMIC_SESSION_BLOCKED", "BLOCKED_LEGAL_HARM");
        assertBlocked(reply(firstPath, "민사", sessionId), "DYNAMIC_SESSION_BLOCKED", null);
        verifyNoInteractions(inputGuard, outputGuard, llm, streamingLlm);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/ai/chat", "/api/ai/chat/stream"})
    void nemoPolicyBlocksAccumulateAcrossBothTransports(String firstPath) throws Exception {
        when(inputGuard.check("민사")).thenReturn(new InputGuardrailResult("BLOCKED", "안전 안내", "INPUT"));
        String sessionId = session();
        assertBlocked(reply(firstPath, "민사", sessionId), "INPUT_GUARDRAIL_BLOCKED", "NEMO_INPUT_GUARDRAIL");
        assertBlocked(reply(otherPath(firstPath), "민사", sessionId),
                "DYNAMIC_SESSION_BLOCKED", "NEMO_INPUT_GUARDRAIL");
        verifyNoInteractions(outputGuard, llm, streamingLlm);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/ai/chat", "/api/ai/chat/stream"})
    void budgetBlocksExposeCommonAndDetailedCodesWhilePreservingLegacyReason(String path) throws Exception {
        String sessionId = session();
        var held = reserveSessionBalance(" " + sessionId + " ", BigDecimal.ZERO);
        assertThat(held.allowed()).isTrue();
        try {
            var response = reply(path, "민사", sessionId);
            assertBlocked(response, "COST_LIMIT_BLOCKED", "SESSION_BUDGET_EXCEEDED");
            assertThat(response.read("$.reason", String.class)).isEqualTo("SESSION_BUDGET_EXCEEDED");
            verifyNoInteractions(outputGuard, llm, streamingLlm);
        } finally {
            cost.release(held.reservation(), "TEST_CLEANUP");
        }
    }

    @ParameterizedTest
    @CsvSource({"/api/ai/chat,POST", "/api/ai/chat/stream,POST", "/api/legal/guardrail/input,POST", "/actuator/prometheus,GET"})
    void preflightIsHandledBeforeJwtOrBasicAuthentication(String path, String method) throws Exception {
        mvc.perform(options(path).header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString(method)));
        verifyNoInteractions(inputGuard, outputGuard, llm, streamingLlm);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/ai/chat", "/api/ai/chat/stream"})
    void actualRequestsStillRequireJwtAfterCorsIsEnabled(String path) throws Exception {
        mvc.perform(post(path).header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body("민사", session())))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
        verifyNoInteractions(inputGuard, outputGuard, llm, streamingLlm);
    }

    @Test
    void actuatorStillRequiresAdminBasicAuthentication() throws Exception {
        mvc.perform(get("/actuator/prometheus").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.AUTHORIZATION, basic("user:user123")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/prometheus").header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.AUTHORIZATION, basic("admin:admin123")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
    }

    @Test
    void sseHoldsAllChunksUntilCompletionAndMasksPiiAcrossProviderBoundaries() throws Exception {
        String prefix = "안내 ".repeat(70);
        String[] pieces = {prefix, "연락처 010-", "1234-", "5678, 이메일 ho", "ng@example.com, 주민번호 900101-",
                "1234567, 카드 1234-5678-", "9012-3456, 계좌 123-456-", "789012"};
        var completed = new AtomicReference<Consumer<StreamingLlmClient.Usage>>();
        doAnswer(call -> {
            call.<com.ohgiraffers.handlermethod.llm.LlmStreamControl>getArgument(5).dispatch(() -> {});
            Consumer<String> chunk = call.getArgument(2);
            for (String piece : pieces) {
                chunk.accept(piece);
            }
            completed.set(call.getArgument(3));
            return null;
        }).when(streamingLlm).stream(anyString(), anyString(), any(), any(), any(), any());

        MvcResult started = mvc.perform(post("/api/ai/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, authorization).contentType(MediaType.APPLICATION_JSON)
                        .content(body("민사", session())))
                .andExpect(status().isOk()).andReturn();
        assertThat(started.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("event:token", "010-", "hong@", "900101");
        verifyNoInteractions(outputGuard);

        completed.get().accept(new StreamingLlmClient.Usage("test-model", 10, 4));
        started.getAsyncResult(5_000);
        String stream = mvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(streamText(stream)).isEqualTo(prefix
                + "연락처 010-1***-****, 이메일 h***@example.com, 주민번호 900101-*******, "
                + "카드 1234-****-****-****, 계좌 123-***-******");
        assertThat(stream).doesNotContain("010-1234-5678", "hong@example.com", "900101-1234567",
                "1234-5678-9012-3456", "123-456-789012");
        verify(outputGuard).check(String.join("", pieces));
        assertThat(registry.get("legal.ai.privacy.output.masked").tag("piiType", "PHONE").counter().count())
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void sseMasksTheOutputGuardsModifiedContent() throws Exception {
        when(outputGuard.check(anyString())).thenReturn(
                new OutputGuardrailResult("MODIFIED", "연락처 010-1234-5678", "OUTPUT"));
        MvcResult started = mvc.perform(post("/api/ai/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, authorization).contentType(MediaType.APPLICATION_JSON)
                        .content(body("민사", session())))
                .andExpect(status().isOk()).andReturn();
        started.getAsyncResult(5_000);
        String stream = mvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(streamText(stream)).isEqualTo("연락처 010-1***-****");
        assertThat(stream).doesNotContain("010-1234-5678");
    }

    @Test
    void sseOutputPolicyBlockNeverEmitsBufferedPersonalData() throws Exception {
        when(outputGuard.check(anyString())).thenReturn(
                new OutputGuardrailResult("BLOCKED", "차단", "OUTPUT"));
        doAnswer(call -> {
            call.<com.ohgiraffers.handlermethod.llm.LlmStreamControl>getArgument(5).dispatch(() -> {});
            Consumer<String> chunk = call.getArgument(2);
            Consumer<StreamingLlmClient.Usage> complete = call.getArgument(3);
            chunk.accept("연락처 010-");
            chunk.accept("1234-5678");
            complete.accept(new StreamingLlmClient.Usage("test-model", 10, 4));
            return null;
        }).when(streamingLlm).stream(anyString(), anyString(), any(), any(), any(), any());
        MvcResult started = mvc.perform(post("/api/ai/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, authorization).contentType(MediaType.APPLICATION_JSON)
                        .content(body("민사", session())))
                .andExpect(status().isOk()).andReturn();
        started.getAsyncResult(5_000);
        String stream = mvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(stream).contains("OUTPUT_POLICY_BLOCKED").doesNotContain("event:token");
        // Random trace IDs can contain "010-"; inspect the delivered text for personal data.
        assertThat(streamText(stream)).doesNotContain("010-", "1234-5678");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/ai/chat", "/api/ai/chat/stream"})
    void missingUsageIsExposedAsAnEstimatedSettlement(String path) throws Exception {
        when(llm.chat(anyString(), anyString())).thenReturn(
                new LlmClient.LlmResponse("전자소송 안내", "test-model", 0, 0, false));
        doAnswer(call -> {
            call.<com.ohgiraffers.handlermethod.llm.LlmStreamControl>getArgument(5).dispatch(() -> {});
            Consumer<String> chunk = call.getArgument(2);
            Consumer<StreamingLlmClient.Usage> complete = call.getArgument(3);
            chunk.accept("전자소송 안내");
            complete.accept(new StreamingLlmClient.Usage("test-model", 0, 0, false));
            return null;
        }).when(streamingLlm).stream(anyString(), anyString(), any(), any(), any(), any());
        double before = registry.find("ai.cost.usage.missing").tag("stage", "answer").counters().stream()
                .mapToDouble(counter -> counter.count()).sum();
        var response = reply(path, "민사", session());
        assertThat(response.read("$.blocked", Boolean.class)).isFalse();
        assertThat(response.read("$.usageAvailable", Boolean.class)).isFalse();
        assertThat(response.read("$.costSource", String.class)).isEqualTo("ESTIMATED");
        if (path.endsWith("/stream")) {
            assertThat(response.read("$.promptTokens", Integer.class)).isNull();
            assertThat(response.read("$.completionTokens", Integer.class)).isNull();
        }
        assertThat(registry.find("ai.cost.usage.missing").tag("stage", "answer").counters().stream()
                .mapToDouble(counter -> counter.count()).sum()).isEqualTo(before + 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/ai/chat", "/api/ai/chat/stream"})
    void reportedZeroTokensRemainReportedRatherThanEstimated(String path) throws Exception {
        when(llm.chat(anyString(), anyString())).thenReturn(
                new LlmClient.LlmResponse("전자소송 안내", "test-model", 0, 0));
        doAnswer(call -> {
            call.<com.ohgiraffers.handlermethod.llm.LlmStreamControl>getArgument(5).dispatch(() -> {});
            Consumer<String> chunk = call.getArgument(2);
            Consumer<StreamingLlmClient.Usage> complete = call.getArgument(3);
            chunk.accept("전자소송 안내");
            complete.accept(new StreamingLlmClient.Usage("test-model", 0, 0));
            return null;
        }).when(streamingLlm).stream(anyString(), anyString(), any(), any(), any(), any());
        var response = reply(path, "민사", session());
        assertThat(response.read("$.usageAvailable", Boolean.class)).isTrue();
        assertThat(response.read("$.costSource", String.class)).isEqualTo("REPORTED");
        if (path.endsWith("/stream")) {
            assertThat(response.read("$.promptTokens", Integer.class)).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"input", "output"})
    void standaloneChecksReportUsageAndShareTheCostLedger(String kind) throws Exception {
        when(inputGuard.check(anyString())).thenReturn(new InputGuardrailResult(
                "BLOCKED", "정책 차단", "INPUT", new GuardrailUsage(20, 5)));
        when(outputGuard.check(anyString())).thenReturn(new OutputGuardrailResult(
                "BLOCKED", "정책 차단", "OUTPUT", new GuardrailUsage(20, 5)));
        double before = stageGauge("actual", kind + "_guardrail");
        mvc.perform(post("/api/legal/guardrail/" + kind).header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                                kind.equals("input") ? "message" : "response", "민사", "sessionId", session()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.usageAvailable").value(true))
                .andExpect(jsonPath("$.costSource").value("REPORTED"));
        assertThat(stageGauge("actual", kind + "_guardrail") - before).isCloseTo(0.000006,
                org.assertj.core.data.Offset.offset(0.00000001));
        assertThat(registry.get("ai.cost.reserved.usd").gauge().value()).isZero();
        verifyNoInteractions(llm, streamingLlm);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/ai/chat", "/api/ai/chat/stream",
            "/api/legal/guardrail/input", "/api/legal/guardrail/output"})
    void allFourApisRejectExhaustedSessionsBeforeAnyPaidCall(String path) throws Exception {
        String sessionId = session();
        var held = reserveSessionBalance(sessionId, BigDecimal.ZERO).reservation();
        try {
            if (path.contains("/guardrail/")) {
                mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, authorization)
                                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                                        path.endsWith("/output") ? "response" : "message", "민사", "sessionId", sessionId))))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(false))
                        .andExpect(jsonPath("$.reasonCode").value("COST_LIMIT_BLOCKED"))
                        .andExpect(jsonPath("$.detailCode").value("SESSION_BUDGET_EXCEEDED"));
            } else {
                assertBlocked(reply(path, "민사", sessionId), "COST_LIMIT_BLOCKED", "SESSION_BUDGET_EXCEEDED");
            }
            verifyNoInteractions(inputGuard, outputGuard, llm, streamingLlm);
        } finally {
            cost.release(held, "TEST_CLEANUP");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/ai/chat", "/api/ai/chat/stream"})
    void outputBlocksKeepBothGenerationAndInspectionCosts(String path) throws Exception {
        when(outputGuard.check(anyString())).thenReturn(new OutputGuardrailResult("BLOCKED", "차단", "OUTPUT"));
        double answerBefore = stageGauge("actual", "answer");
        double outputBefore = stageGauge("estimated.usage", "output_guardrail");
        assertBlocked(reply(path, "민사", session()), "OUTPUT_POLICY_BLOCKED", null);
        assertThat(stageGauge("actual", "answer")).isGreaterThan(answerBefore);
        assertThat(stageGauge("estimated.usage", "output_guardrail")).isGreaterThan(outputBefore);
        assertThat(registry.get("ai.cost.reserved.usd").gauge().value()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/ai/chat", "/api/ai/chat/stream"})
    void outputBudgetDenialDoesNotDiscardAlreadyGeneratedCost(String path) throws Exception {
        String sessionId = session();
        // Leave enough for the whole-request precheck (input check + answer + output check, about $0.0038 here),
        // then report a provider overrun which leaves no output-Guard budget.
        var spent = reserveSessionBalance(sessionId, new BigDecimal("0.0040")).reservation();
        cost.recordActualUsage(spent, 0, 1);
        when(llm.chat(anyString(), anyString())).thenReturn(new LlmClient.LlmResponse("안내", "test", 30000, 0));
        doAnswer(call -> {
            call.<com.ohgiraffers.handlermethod.llm.LlmStreamControl>getArgument(5).dispatch(() -> {});
            Consumer<String> chunk = call.getArgument(2);
            Consumer<StreamingLlmClient.Usage> complete = call.getArgument(3);
            chunk.accept("안내");
            complete.accept(new StreamingLlmClient.Usage("test", 30000, 0));
            return null;
        }).when(streamingLlm).stream(anyString(), anyString(), any(), any(), any(), any());
        double before = stageGauge("actual", "answer");
        assertBlocked(reply(path, "민사", sessionId), "COST_LIMIT_BLOCKED", "SESSION_BUDGET_EXCEEDED");
        assertThat(stageGauge("actual", "answer") - before).isCloseTo(0.0045,
                org.assertj.core.data.Offset.offset(0.00000001));
        verifyNoInteractions(outputGuard);
    }

    @ParameterizedTest
    @ValueSource(strings = {"timeout", "disconnect", "completion"})
    void sseAbandonmentSettlesOnceAndIgnoresLateProviderCompletion(String lifecycle) throws Exception {
        var emitter = mock(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.class);
        var timeout = new AtomicReference<Runnable>();
        var completion = new AtomicReference<Runnable>();
        var disconnect = new AtomicReference<Consumer<Throwable>>();
        var providerComplete = new AtomicReference<Consumer<StreamingLlmClient.Usage>>();
        doAnswer(c -> { timeout.set(c.getArgument(0)); return null; }).when(emitter).onTimeout(any());
        doAnswer(c -> { completion.set(c.getArgument(0)); return null; }).when(emitter).onCompletion(any());
        doAnswer(c -> { disconnect.set(c.getArgument(0)); return null; }).when(emitter).onError(any());
        doAnswer(c -> { c.<com.ohgiraffers.handlermethod.llm.LlmStreamControl>getArgument(5).dispatch(() -> {}); providerComplete.set(c.getArgument(3)); return null; })
                .when(streamingLlm).stream(anyString(), anyString(), any(), any(), any(), any());
        double before = stageGauge("estimated.usage", "answer");
        streams.stream(new AiChatRequest("민사", session()), emitter);
        if (lifecycle.equals("timeout")) { timeout.get().run(); }
        else if (lifecycle.equals("disconnect")) { disconnect.get().accept(new java.io.IOException("closed")); }
        else { completion.get().run(); }
        double settledCost = stageGauge("estimated.usage", "answer");
        assertThat(settledCost).isGreaterThan(before);
        providerComplete.get().accept(new StreamingLlmClient.Usage("test", 100, 50));
        timeout.get().run();
        completion.get().run();
        assertThat(stageGauge("estimated.usage", "answer")).isEqualTo(settledCost);
        assertThat(registry.get("ai.cost.reserved.usd").gauge().value()).isZero();
        verifyNoInteractions(outputGuard);
    }

    @Test
    void streamFailureAfterUsageRetainsReportedCostAndPublishesAccountingMetadata() throws Exception {
        doAnswer(call -> {
            var control = call.<com.ohgiraffers.handlermethod.llm.LlmStreamControl>getArgument(5);
            control.dispatch(() -> {});
            control.reportUsage(new StreamingLlmClient.Usage("test-model", 10, 4));
            call.<Consumer<String>>getArgument(2).accept("연락처 010-1234-5678");
            call.<Consumer<Throwable>>getArgument(4).accept(new java.net.http.HttpTimeoutException("synthetic timeout"));
            return null;
        }).when(streamingLlm).stream(anyString(), anyString(), any(), any(), any(), any());
        double before = stageGauge("actual", "answer");
        var response = reply("/api/ai/chat/stream", "민사", session());
        assertThat(response.read("$.costSource", String.class)).isEqualTo("REPORTED");
        assertThat(response.read("$.usageAvailable", Boolean.class)).isTrue();
        assertThat(response.read("$.promptTokens", Integer.class)).isEqualTo(10);
        assertThat(stageGauge("actual", "answer") - before).isCloseTo(0.0000039, org.assertj.core.data.Offset.offset(1e-8));
        assertThat(response.jsonString()).doesNotContain("010-1234-5678");
        verifyNoInteractions(outputGuard);
    }

    @Test
    void failureBeforeHttpDispatchReleasesInsteadOfInventingUsage() throws Exception {
        doAnswer(call -> {
            call.<Consumer<Throwable>>getArgument(4).accept(new IllegalStateException("missing configuration"));
            return null;
        }).when(streamingLlm).stream(anyString(), anyString(), any(), any(), any(), any());
        double before = stageGauge("accounted", "answer");
        var response = reply("/api/ai/chat/stream", "민사", session());
        assertThat(response.read("$.costSource", String.class)).isEqualTo("RELEASED");
        assertThat(response.read("$.usageAvailable", Boolean.class)).isFalse();
        assertThat(stageGauge("accounted", "answer")).isEqualTo(before);
        assertThat(registry.get("ai.cost.reserved.usd").gauge().value()).isZero();
    }

    @Test
    void knownUsageDisconnectCompletionAndErrorRaceSettleReportedCostOnceAndCancelTransport() throws Exception {
        var emitter = mock(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.class);
        var timeout = new AtomicReference<Runnable>(); var completion = new AtomicReference<Runnable>();
        var disconnect = new AtomicReference<Consumer<Throwable>>();
        var providerComplete = new AtomicReference<Consumer<StreamingLlmClient.Usage>>();
        var providerError = new AtomicReference<Consumer<Throwable>>();
        var controlRef = new AtomicReference<com.ohgiraffers.handlermethod.llm.LlmStreamControl>();
        var cancelled = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(c -> { timeout.set(c.getArgument(0)); return null; }).when(emitter).onTimeout(any());
        doAnswer(c -> { completion.set(c.getArgument(0)); return null; }).when(emitter).onCompletion(any());
        doAnswer(c -> { disconnect.set(c.getArgument(0)); return null; }).when(emitter).onError(any());
        doAnswer(c -> {
            var control = c.<com.ohgiraffers.handlermethod.llm.LlmStreamControl>getArgument(5);
            control.dispatch(() -> {}); control.onCancel(cancelled::incrementAndGet);
            control.reportUsage(new StreamingLlmClient.Usage("test-model", 10, 4)); controlRef.set(control);
            providerComplete.set(c.getArgument(3)); providerError.set(c.getArgument(4)); return null;
        }).when(streamingLlm).stream(anyString(), anyString(), any(), any(), any(), any());
        double before = stageGauge("actual", "answer"), estimatedBefore = stageGauge("estimated.usage", "answer");
        streams.stream(new AiChatRequest("민사", session()), emitter);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (Runnable callback : java.util.List.<Runnable>of(
                    () -> providerComplete.get().accept(new StreamingLlmClient.Usage("test-model", 10, 4)),
                    () -> providerError.get().accept(new IllegalStateException("synthetic error")),
                    () -> disconnect.get().accept(new java.io.IOException("closed")),
                    () -> timeout.get().run(), () -> completion.get().run())) {
                tasks.add(pool.submit(() -> { start.await(); callback.run(); return null; }));
            }
            start.countDown();
            for (var task : tasks) task.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(stageGauge("actual", "answer") - before).isCloseTo(0.0000039, org.assertj.core.data.Offset.offset(1e-8));
        assertThat(stageGauge("estimated.usage", "answer")).isEqualTo(estimatedBefore);
        assertThat(registry.get("ai.cost.reserved.usd").gauge().value()).isZero();
        assertThat(cancelled.get()).isEqualTo(1); assertThat(controlRef.get().cancelled()).isTrue();
        verify(outputGuard, org.mockito.Mockito.atMost(1)).check(anyString());
    }

    @Test
    void outputBlockStillReportsGeneratedUsageAndNoUnmaskedTokenEvents() throws Exception {
        when(outputGuard.check(anyString())).thenReturn(new OutputGuardrailResult("BLOCKED", "차단", "OUTPUT"));
        double before = stageGauge("actual", "answer");
        var response = reply("/api/ai/chat/stream", "민사", session());
        assertThat(response.read("$.blocked", Boolean.class)).isTrue();
        assertThat(response.read("$.costSource", String.class)).isEqualTo("REPORTED");
        assertThat(response.read("$.completionTokens", Integer.class)).isEqualTo(4);
        assertThat(stageGauge("actual", "answer") - before).isCloseTo(0.0000039, org.assertj.core.data.Offset.offset(1e-8));
    }

    @Test
    void prometheusExposesAllThreeCostStagesAndTheirTotal() throws Exception {
        reply("/api/ai/chat", "민사", session());
        mvc.perform(get("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, basic("admin:admin123")))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.allOf(containsString("ai_cost_stage_accounted_usd"),
                                containsString("stage=\"input_guardrail\""), containsString("stage=\"answer\""),
                                containsString("stage=\"output_guardrail\""), containsString("ai_cost_accounted_usd"))));
        double sum = stageGauge("accounted", "input_guardrail") + stageGauge("accounted", "answer")
                + stageGauge("accounted", "output_guardrail");
        assertThat(sum).isCloseTo(registry.get("ai.cost.accounted.usd").gauge().value(),
                org.assertj.core.data.Offset.offset(0.00000001));
    }

    private AiCostControlService.CostReservationDecision reserveSessionBalance(String sessionId, BigDecimal leave) {
        BigDecimal budget = BigDecimal.valueOf(registry.get("ai.cost.session.budget.usd").gauge().value());
        return cost.reserveStageWithBlock(AiCostControlService.CostStage.ANSWER, 0, 1,
                "synthetic-budget-fill", sessionId, BigDecimal.ZERO, budget.subtract(leave).multiply(new BigDecimal("1000")));
    }

    private double stageGauge(String name, String stage) {
        return registry.get("ai.cost.stage." + name + ".usd").tag("stage", stage).gauge().value();
    }

    private String streamText(String stream) {
        StringBuilder text = new StringBuilder();
        stream.lines().filter(line -> line.startsWith("data:")).forEach(line -> {
            String value = JsonPath.parse(line.substring(5)).read("$.text", String.class);
            if (value != null) {
                text.append(value);
            }
        });
        return text.toString();
    }

    private DocumentContext reply(String path, String message, String sessionId) throws Exception {
        MvcResult result = mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, authorization)
                        .header(HttpHeaders.ORIGIN, ORIGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(body(message, sessionId)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andReturn();
        if (path.endsWith("/stream")) {
            assertThat(result.getRequest().isAsyncStarted()).isTrue();
            result.getAsyncResult(5_000);
            result = mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn();
            String stream = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            String lastData = stream.lines().filter(line -> line.startsWith("data:"))
                    .reduce((first, next) -> next).orElseThrow();
            return JsonPath.parse(lastData.substring("data:".length()));
        }
        return JsonPath.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private void assertBlocked(DocumentContext response, String reasonCode, String detailCode) {
        assertThat(response.read("$.blocked", Boolean.class)).isTrue();
        assertThat(response.read("$.fallback", Boolean.class)).isTrue();
        assertThat(response.read("$.reasonCode", String.class)).isEqualTo(reasonCode);
        assertThat(response.read("$.detailCode", String.class)).isEqualTo(detailCode);
    }

    private String body(String message, String sessionId) {
        return mapper.writeValueAsString(Map.of("message", message, "sessionId", sessionId));
    }

    private String session() {
        return UUID.randomUUID().toString();
    }

    private String otherPath(String path) {
        return path.endsWith("/stream") ? "/api/ai/chat" : "/api/ai/chat/stream";
    }

    private String basic(String credentials) {
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
