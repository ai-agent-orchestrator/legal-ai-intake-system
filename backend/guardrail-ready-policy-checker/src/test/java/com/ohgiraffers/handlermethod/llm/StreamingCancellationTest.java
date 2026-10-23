package com.ohgiraffers.handlermethod.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class StreamingCancellationTest {
    HttpServer server;
    ExecutorService workers;
    String base;
    volatile String body = "data: [DONE]\n\n";
    volatile boolean stall;
    CountDownLatch release;
    AtomicInteger requests;
    @BeforeEach void start() throws Exception {
        release = new CountDownLatch(1); requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        workers = Executors.newVirtualThreadPerTaskExecutor(); server.setExecutor(workers);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (var out = exchange.getResponseBody()) {
                out.write(body.getBytes(StandardCharsets.UTF_8)); out.flush();
                if (stall) release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
        server.start(); base = "http://127.0.0.1:" + server.getAddress().getPort();
    }
    @AfterEach void stop() { release.countDown(); server.stop(0); workers.shutdownNow(); }
    OpenAiCompatibleStreamingLlmClient client(String key, long timeout) {
        return new OpenAiCompatibleStreamingLlmClient(new ObjectMapper(), base, key, "test", 256, timeout);
    }
    static final String USAGE = "data: {\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":4}}\n\n";

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void providerErrorRetainsAnyVerifiedUsageAndTerminatesOnlyOnce(boolean reported) throws Exception {
        body = (reported ? USAGE : "") + "data: {\"error\":{\"message\":\"test error\"}}\n\ndata: [DONE]\n\n";
        var control = new LlmStreamControl(); var failed = new CompletableFuture<Throwable>(); var terminals = new AtomicInteger();
        client("test-key", 2000).stream("system", "synthetic", ignored -> {},
                usage -> { terminals.incrementAndGet(); failed.completeExceptionally(new AssertionError("unexpected success")); },
                error -> { terminals.incrementAndGet(); failed.complete(error); }, control);
        assertThat(failed.get(5, TimeUnit.SECONDS)).isInstanceOf(IllegalStateException.class);
        assertThat(terminals.get()).isEqualTo(1);
        assertThat(control.snapshot().dispatched()).isTrue();
        assertThat(control.cancelled()).isTrue();
        if (reported) assertThat(control.snapshot().usage().promptTokens()).isEqualTo(10);
        else assertThat(control.snapshot().usage()).isNull();
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void configuredDeadlineCoversAStalledResponseBodyAndPreservesUsage(boolean reported) throws Exception {
        body = (reported ? USAGE : "") + ": heartbeat\n\n"; stall = true;
        var control = new LlmStreamControl(); var failed = new CompletableFuture<Throwable>();
        client("test-key", 1000).stream("system", "synthetic", ignored -> {},
                ignored -> failed.completeExceptionally(new AssertionError("unexpected success")), failed::complete, control);
        assertThat(failed.get(5, TimeUnit.SECONDS)).isInstanceOf(HttpTimeoutException.class);
        assertThat(control.cancelled()).isTrue();
        assertThat(requests.get()).isEqualTo(1);
        if (reported) assertThat(control.snapshot().usage().completionTokens()).isEqualTo(4);
        else assertThat(control.snapshot().usage()).isNull();
    }

    @Test void browserCancellationClosesTransportAndStopsFurtherDelivery() throws Exception {
        body = "data: {\"choices\":[{\"delta\":{\"content\":\"synthetic\"}}]}\n\n"; stall = true;
        var control = new LlmStreamControl(); var chunk = new CountDownLatch(1); var chunks = new AtomicInteger();
        var failed = new CompletableFuture<Throwable>();
        client("test-key", 2000).stream("system", "synthetic", text -> { chunks.incrementAndGet(); chunk.countDown(); },
                ignored -> failed.completeExceptionally(new AssertionError("unexpected success")), failed::complete, control);
        assertThat(chunk.await(5, TimeUnit.SECONDS)).isTrue();
        control.cancel(); control.cancel();
        assertThat(failed.get(5, TimeUnit.SECONDS)).isInstanceOf(CancellationException.class);
        assertThat(chunks.get()).isEqualTo(1);
        assertThat(control.snapshot().dispatched()).isTrue();
    }

    @Test void missingConfigurationAndCancellationBeforeDispatchNeverSendHttp() throws Exception {
        var control = new LlmStreamControl(); var failed = new CompletableFuture<Throwable>();
        client("", 1000).stream("system", "synthetic", ignored -> {}, ignored -> {}, failed::complete, control);
        assertThat(failed.get(5, TimeUnit.SECONDS)).isInstanceOf(IllegalStateException.class);
        assertThat(control.snapshot().dispatched()).isFalse();
        var cancelled = new LlmStreamControl(); cancelled.cancel();
        var stopped = new CompletableFuture<Throwable>();
        client("key", 1000).stream("system", "synthetic", ignored -> {}, ignored -> {}, stopped::complete, cancelled);
        assertThat(stopped.get(5, TimeUnit.SECONDS)).isInstanceOf(CancellationException.class);
        assertThat(cancelled.snapshot().dispatched()).isFalse();
        assertThat(requests.get()).isZero();
    }

    @Test void abruptEofDoesNotBecomeSuccessEvenAfterUsageWasReceived() throws Exception {
        body = USAGE;
        var control = new LlmStreamControl(); var failed = new CompletableFuture<Throwable>();
        client("key", 1000).stream("system", "synthetic", ignored -> {},
                ignored -> failed.completeExceptionally(new AssertionError("unexpected success")), failed::complete, control);
        assertThat(failed.get(5, TimeUnit.SECONDS)).hasMessageContaining("before [DONE]");
        assertThat(control.snapshot().usage().usageAvailable()).isTrue();
    }

    @Test void cancellationRegistrationRaceStillClosesEveryResourceOnceAndFreezesUsage() {
        var control = new LlmStreamControl(); var closures = new AtomicInteger();
        control.reportUsage(new StreamingLlmClient.Usage("test", 0, 0));
        control.onCancel(closures::incrementAndGet); control.cancel(); control.cancel();
        control.onCancel(closures::incrementAndGet);
        control.reportUsage(new StreamingLlmClient.Usage("test", 1000, 1000));
        assertThat(closures.get()).isEqualTo(2);
        assertThat(control.snapshot().usage().promptTokens()).isZero();
        assertThat(control.dispatch(() -> { throw new AssertionError("must not start"); })).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"NORMAL", "BLOCKED", "ERROR", "TIMEOUT"})
    void realHttpClientAndSsePipelineSettleBeforeGuardOrDeliveryAndNeverExposeRawPii(String mode) throws Exception {
        body = "data: {\"choices\":[{\"delta\":{\"content\":\"연락처 010-\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"1234-5678\"}}]}\n\n" + USAGE
                + (mode.equals("ERROR") ? "data: {\"error\":{}}\n\n" : mode.equals("TIMEOUT") ? "" : "data: [DONE]\n\n");
        stall = mode.equals("TIMEOUT");
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var cost = new com.ohgiraffers.handlermethod.cost.AiCostControlService(new java.math.BigDecimal("10"),
                new java.math.BigDecimal("10"), new java.math.BigDecimal("10"), new java.math.BigDecimal("0.15"),
                new java.math.BigDecimal("0.60"), 256, 10, 100, registry);
        com.ohgiraffers.handlermethod.guardrail.InputGuardrailClient input = text ->
                new com.ohgiraffers.handlermethod.guardrail.InputGuardrailResult("PASSED", text, "INPUT",
                        new com.ohgiraffers.handlermethod.guardrail.GuardrailUsage(0, 0));
        com.ohgiraffers.handlermethod.guardrail.OutputGuardrailClient output = text ->
                new com.ohgiraffers.handlermethod.guardrail.OutputGuardrailResult(mode.equals("BLOCKED") ? "BLOCKED" : "PASSED",
                        text, "OUTPUT", new com.ohgiraffers.handlermethod.guardrail.GuardrailUsage(0, 0));
        var guards = new com.ohgiraffers.handlermethod.cost.CostedGuardrailService(input, output, cost, "test",
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, 0, 1);
        var service = new com.ohgiraffers.handlermethod.service.AiStreamingService(guards, client("test-key", 1000), cost,
                new com.ohgiraffers.handlermethod.support.AiMetricRecorder(registry),
                new com.ohgiraffers.handlermethod.riskengine.LegalFixedGuardService(),
                new com.ohgiraffers.handlermethod.riskengine.LegalDynamicGuardService(registry),
                new com.ohgiraffers.handlermethod.riskengine.PrivacyOutputGuardService(), "system", "test", 1);
        var emitter = org.mockito.Mockito.mock(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.class);
        var events = new java.util.concurrent.CopyOnWriteArrayList<com.ohgiraffers.handlermethod.dto.AiStreamEvent>();
        var done = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(call -> {
            var builder = call.<org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder>getArgument(0);
            for (var data : builder.build()) {
                if (data.getData() instanceof com.ohgiraffers.handlermethod.dto.AiStreamEvent event) events.add(event);
            }
            return null;
        }).when(emitter).send(org.mockito.ArgumentMatchers.any(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder.class));
        org.mockito.Mockito.doAnswer(call -> { done.countDown(); return null; }).when(emitter).complete();
        service.stream(new com.ohgiraffers.handlermethod.dto.AiChatRequest("민사", "synthetic"), emitter);
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        var terminal = events.getLast();
        assertThat(terminal.costSource()).isEqualTo("REPORTED");
        assertThat(terminal.usageAvailable()).isTrue();
        assertThat(registry.get("ai.cost.reserved.usd").gauge().value()).isZero();
        assertThat(registry.get("ai.cost.stage.actual.usd").tag("stage", "answer").gauge().value()).isEqualTo(0.0039);
        String text = events.stream().map(com.ohgiraffers.handlermethod.dto.AiStreamEvent::text)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.joining());
        assertThat(text).doesNotContain("010-1234-5678");
        if (mode.equals("NORMAL")) assertThat(text).isEqualTo("연락처 010-1***-****");
        else assertThat(text).doesNotContain("010-");
    }
}
