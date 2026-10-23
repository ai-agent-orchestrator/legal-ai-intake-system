package com.ohgiraffers.handlermethod.guardrail;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class GuardrailUsageClientTest {
    private HttpServer server;
    private String baseUrl;
    private volatile String response;
    private int requests;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/", exchange -> {
            requests++;
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() { server.stop(0); }

    @ParameterizedTest
    @ValueSource(strings = {"ABSENT", "null", "{}", "{\"prompt_tokens\":1}",
            "{\"prompt_tokens\":-1,\"completion_tokens\":2}",
            "{\"prompt_tokens\":1.5,\"completion_tokens\":2}",
            "{\"prompt_tokens\":2147483648,\"completion_tokens\":2}"})
    void oldOrIncompleteAdapterResponsesRemainUnknown(String usage) {
        response = "{\"status\":\"PASSED\",\"content\":\"안내\",\"rail\":\"test\""
                + (usage.equals("ABSENT") ? "" : ",\"usage\":" + usage) + "}";
        var input = new NemoInputGuardrailClient(RestClient.builder(), baseUrl, new SimpleMeterRegistry(), 1000).check("민사");
        var output = new NemoOutputGuardrailClient(RestClient.builder(), baseUrl, new SimpleMeterRegistry(), 1000).check("안내");
        assertThat(input.allowed()).isTrue();
        assertThat(input.usage() != null && input.usage().available()).isFalse();
        assertThat(output.usage() != null && output.usage().available()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 20})
    void adapterTokenCountsIncludingZeroArePreserved(int tokens) {
        response = "{\"status\":\"BLOCKED\",\"content\":\"차단\",\"rail\":\"test\",\"usage\":{\"prompt_tokens\":"
                + tokens + ",\"completion_tokens\":" + tokens + "}}";
        var input = new NemoInputGuardrailClient(RestClient.builder(), baseUrl, new SimpleMeterRegistry(), 1000).check("민사");
        var output = new NemoOutputGuardrailClient(RestClient.builder(), baseUrl, new SimpleMeterRegistry(), 1000).check("안내");
        assertThat(input.allowed()).isFalse();
        assertThat(input.usage().available()).isTrue();
        assertThat(input.usage().prompt_tokens()).isEqualTo(tokens);
        assertThat(output.usage().completion_tokens()).isEqualTo(tokens);
    }

    @Test
    void localOutputRejectionReportsKnownZeroWithoutRemoteCall() {
        var output = new NemoOutputGuardrailClient(RestClient.builder(), baseUrl, new SimpleMeterRegistry(), 1000).check("");
        assertThat(output.allowed()).isFalse();
        assertThat(output.usage().available()).isTrue();
        assertThat(output.usage().prompt_tokens().intValue()).isZero();
        assertThat(requests).isZero();
    }
}
