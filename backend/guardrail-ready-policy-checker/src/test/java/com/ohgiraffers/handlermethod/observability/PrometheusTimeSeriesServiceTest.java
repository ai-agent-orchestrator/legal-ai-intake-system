package com.ohgiraffers.handlermethod.observability;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class PrometheusTimeSeriesServiceTest {
    @Test
    void realHttpRangeQueryPreservesLabelsFractionsZeroAndWarningsWithoutCredentials() throws Exception {
        var called = new AtomicReference<String>();
        var auth = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/query_range", exchange -> {
            called.set(URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = """
                    {"status":"success","data":{"resultType":"matrix","result":[
                      {"metric":{"signal":"policy","tool":"database"},"values":[[100,"0"],[115,"1.25"]]},
                      {"metric":{"signal":"retry"},"values":[[100,"NaN"]]}]},"warnings":["partial data"]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        try {
            var service = new PrometheusTimeSeriesService("http://127.0.0.1:" + server.getAddress().getPort(), "legal-ai-intake", new ObjectMapper());
            var value = service.read();
            assertThat((Long) value.get("end") - (Long) value.get("start")).isEqualTo(3600);
            assertThat(value.get("series").toString()).contains("1.25", "NaN", "database");
            assertThat(value.get("warnings").toString()).contains("partial data");
            assertThat(called.get()).contains("job=\"legal-ai-intake\"", "[5m]", "\"signal\",\"policy\"", "&step=15&timeout=5s&limit=128");
            assertThat(auth.get()).isNull();
        } finally { server.stop(0); }
    }

    @Test
    void upstreamFailureAndMalformedSuccessReturnUnavailableWithoutLeakingResponse() throws Exception {
        var response = new AtomicReference<>("{\"status\":\"error\",\"error\":\"private server data\"}");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        try {
            var service = new PrometheusTimeSeriesService("http://127.0.0.1:" + server.getAddress().getPort(), "legal-ai-intake", new ObjectMapper());
            for (String body : new String[]{response.get(), "<html>private server data</html>", "x".repeat(2_000_001),
                    "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}"}) {
                response.set(body);
                assertThatThrownBy(service::read).isInstanceOf(ResponseStatusException.class)
                        .hasMessageContaining("503").hasMessageNotContaining("private server data");
            }
        } finally { server.stop(0); }
    }

    @Test
    void queryCannotBeInjectedThroughServerJobOrCredentialsInUrl() {
        assertThatThrownBy(() -> new PrometheusTimeSeriesService("http://user:secret@localhost:9090", "safe", new ObjectMapper())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PrometheusTimeSeriesService("http://localhost:9090", "bad\"} or up", new ObjectMapper())).isInstanceOf(IllegalArgumentException.class);
    }
}
