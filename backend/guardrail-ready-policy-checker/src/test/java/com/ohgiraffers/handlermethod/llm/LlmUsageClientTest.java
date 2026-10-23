package com.ohgiraffers.handlermethod.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LlmUsageClientTest {
    private HttpServer server;
    private String baseUrl;
    private volatile String responseBody;
    private volatile String contentType;
    private volatile String requestBody;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sync", "sse"})
    void bothTransportsSendTheFullStoryAndTheSame2048CompletionTokenCap(String transport) throws Exception {
        String story = "민사" + "가".repeat(98);
        if ("sync".equals(transport)) {
            contentType = "application/json";
            responseBody = "{\"choices\":[{\"message\":{\"content\":\"안내\"}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":4}}";
            new OpenAiCompatibleLlmClient(baseUrl, "fixture-key", "gpt-6.1-sol", 2048, 5000).chat("system", story);
        } else {
            contentType = "text/event-stream";
            responseBody = event("안내") + "data: {\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":4}}\n\ndata: [DONE]\n\n";
            var complete = new CompletableFuture<StreamingLlmClient.Usage>();
            new OpenAiCompatibleStreamingLlmClient(new ObjectMapper(), baseUrl, "fixture-key", "gpt-6.1-sol", 2048, 5000)
                    .stream("system", story, ignored -> {}, complete::complete, complete::completeExceptionally);
            complete.get(10, TimeUnit.SECONDS);
        }
        var sent = new ObjectMapper().readTree(requestBody);
        assertThat(sent.path("model").asString()).isEqualTo("gpt-6.1-sol");
        assertThat(sent.path("max_completion_tokens").asInt()).isEqualTo(2048);
        assertThat(sent.path("messages").get(1).path("content").asString()).isEqualTo(story);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABSENT", "null", "{}", "{\"prompt_tokens\":10}",
            "{\"prompt_tokens\":-1,\"completion_tokens\":4}"})
    void synchronousClientMarksUnavailableOrIncompleteUsageAsUnknown(String usage) {
        contentType = "application/json";
        responseBody = "{\"choices\":[{\"message\":{\"content\":\"안내\"}}]"
                + ("ABSENT".equals(usage) ? "" : ",\"usage\":" + usage) + "}";
        var response = new OpenAiCompatibleLlmClient(baseUrl, "test-key", "test-model", 256, 5_000)
                .chat("system", "민사");
        assertThat(response.usageAvailable()).isFalse();
        assertThat(response.content()).isEqualTo("안내");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10})
    void synchronousClientRecognizesReportedUsageIncludingZero(int tokens) {
        contentType = "application/json";
        responseBody = "{\"choices\":[{\"message\":{\"content\":\"안내\"}}],\"usage\":{\"prompt_tokens\":"
                + tokens + ",\"completion_tokens\":" + tokens + "}}";
        var response = new OpenAiCompatibleLlmClient(baseUrl, "test-key", "test-model", 256, 5_000)
                .chat("system", "민사");
        assertThat(response.usageAvailable()).isTrue();
        assertThat(response.promptTokens()).isEqualTo(tokens);
        assertThat(response.completionTokens()).isEqualTo(tokens);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABSENT", "null", "{}", "{\"prompt_tokens\":10}",
            "{\"prompt_tokens\":-1,\"completion_tokens\":4}"})
    void streamingClientPreservesWhitespaceAndMarksIncompleteUsageAsUnknown(String usage) throws Exception {
        contentType = "text/event-stream";
        responseBody = event("안내") + event(" ") + event("완료")
                + ("ABSENT".equals(usage) ? "" : "data: {\"usage\":" + usage + "}\n\n")
                + "data: [DONE]\n\n";
        StringBuilder chunks = new StringBuilder();
        var complete = new CompletableFuture<StreamingLlmClient.Usage>();
        new OpenAiCompatibleStreamingLlmClient(new ObjectMapper(), baseUrl, "test-key", "test-model", 256, 5_000)
                .stream("system", "민사", chunks::append, complete::complete, complete::completeExceptionally);
        var result = complete.get(10, TimeUnit.SECONDS);
        assertThat(result.usageAvailable()).isFalse();
        assertThat(chunks.toString()).isEqualTo("안내 완료");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10})
    void streamingClientRecognizesReportedUsageIncludingZero(int tokens) throws Exception {
        contentType = "text/event-stream";
        responseBody = event("안내") + "data: {\"usage\":{\"prompt_tokens\":" + tokens
                + ",\"completion_tokens\":" + tokens + "}}\n\ndata: [DONE]\n\n";
        var complete = new CompletableFuture<StreamingLlmClient.Usage>();
        new OpenAiCompatibleStreamingLlmClient(new ObjectMapper(), baseUrl, "test-key", "test-model", 256, 5_000)
                .stream("system", "민사", ignored -> {}, complete::complete, complete::completeExceptionally);
        var result = complete.get(10, TimeUnit.SECONDS);
        assertThat(result.usageAvailable()).isTrue();
        assertThat(result.promptTokens()).isEqualTo(tokens);
        assertThat(result.completionTokens()).isEqualTo(tokens);
    }

    private String event(String content) {
        return "data: {\"choices\":[{\"delta\":{\"content\":\"" + content + "\"}}]}\n\n";
    }
}
