package com.ohgiraffers.handlermethod.search;

import com.ohgiraffers.handlermethod.riskengine.PrivacyOutputGuardService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.*;

class NaverWebSearchClientTest {
    HttpServer server;
    NaverWebSearchClient client;
    volatile String body = "{\"items\":[]}";
    volatile int status = 200;
    volatile long delay;
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool(r -> { var t = new Thread(r); t.setDaemon(true); return t; }));
        server.createContext("/v1/search/webkr.json", exchange -> {
            try {
                if (delay > 0) Thread.sleep(delay);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
            } catch (Exception ignored) { exchange.close(); }
        });
        server.start(); client = client("id", "secret");
    }
    NaverWebSearchClient client(String id, String secret) {
        return new NaverWebSearchClient(new ObjectMapper(), new PrivacyOutputGuardService(),
                "http://127.0.0.1:" + server.getAddress().getPort(), id, secret, 3, 200);
    }
    @AfterEach void close() { client.close(); server.stop(0); }
    @Test void rejectsUnsafeUrlsIncludingCredentialsAndLocalAddresses() {
        for (String url : List.of("javascript:alert(1)", "file:///C:/secret", "https://name:pass@example.com", "http://127.0.0.1/", "http://10.1.2.3/", "http://[::1]/", "http://localhost/", "http://a.local/", "https://example.com/a b")) assertThat(SearchText.publicUrl(url)).as(url).isFalse();
        assertThat(SearchText.publicUrl("https://law.go.kr/guide?q=loan")).isTrue();
    }
    @Test void rejectsMissingItemsOrOnlyInvalidResultsRatherThanFalseEmptySuccess() {
        for (String invalid : List.of("{}", "{\"items\":null}", "{\"items\":{}}", "{\"items\":[{\"title\":\"bad\",\"link\":\"javascript:bad\"}]}")) {
            body = invalid; assertThatThrownBy(() -> client.search("민사 변호사")).isInstanceOf(SearchFailure.class);
        }
    }
    @Test void limitsResponseBytes() {
        body = "{\"items\":[],\"extra\":\"" + "a".repeat(1_048_576) + "\"}";
        assertThatThrownBy(() -> client.search("민사 변호사")).isInstanceOf(SearchFailure.class);
    }
    @Test void timeoutAndHttpFailureNeverExposeProviderBody() {
        status = 401; body = "provider-secret";
        assertThatThrownBy(() -> client.search("민사 변호사")).hasMessage("SEARCH_PROVIDER_ERROR");
        status = 200; delay = 500;
        assertThatThrownBy(() -> client.search("민사 변호사")).hasMessage("SEARCH_PROVIDER_ERROR");
    }
    @Test void missingCredentialsAndInvalidQueryFailBeforeCallingProvider() {
        var missing = client("", "");
        try { assertThat(missing.configured()).isFalse(); assertThatThrownBy(() -> missing.search("민사")).hasMessage("SEARCH_NOT_CONFIGURED"); }
        finally { missing.close(); }
        assertThatThrownBy(() -> client.search(" ")).hasMessage("INVALID_QUERY");
    }
}
