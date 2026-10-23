package com.ohgiraffers.handlermethod.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

/** Read-only, bounded queries. Browser credentials are never forwarded to Prometheus. */
@Service
public class PrometheusTimeSeriesService {
    private static final Map<String, String> METRICS = new LinkedHashMap<>();
    static {
        METRICS.put("policy", "agent_policy_violation_total");
        METRICS.put("retry", "agent_retry_count_total");
        METRICS.put("tool", "agent_tool_calls_total");
        METRICS.put("external", "agent_external_api_calls_total");
        METRICS.put("error", "agent_tool_errors_total");
    }
    private final URI base;
    private final String job;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public PrometheusTimeSeriesService(@Value("${observability.prometheus.base-url:http://127.0.0.1:9090}") String base,
                                       @Value("${observability.prometheus.job:legal-ai-intake}") String job,
                                       ObjectMapper mapper) {
        this.base = URI.create(base);
        if (!List.of("http", "https").contains(this.base.getScheme()) || this.base.getHost() == null
                || this.base.getUserInfo() != null || this.base.getQuery() != null || this.base.getFragment() != null
                || !List.of("", "/").contains(this.base.getPath())) {
            throw new IllegalArgumentException("Prometheus base URL must be an HTTP(S) origin without credentials");
        }
        if (!job.matches("[A-Za-z0-9_.-]{1,80}")) throw new IllegalArgumentException("Invalid Prometheus job");
        this.job = job;
        this.mapper = mapper;
    }

    String query() {
        // 'or' overlays series. A signal label prevents different metric names with
        // identical labels from being collapsed by PromQL set matching.
        var parts = new java.util.ArrayList<String>();
        METRICS.forEach((signal, metric) -> parts.add("label_replace(increase(" + metric
                + "{job=\"" + job + "\"}[5m]),\"signal\",\"" + signal + "\",\"\",\"\")"));
        parts.add("label_replace(up{job=\"" + job + "\"},\"signal\",\"scrape_up\",\"\",\"\")");
        return String.join(" or ", parts);
    }

    public Map<String, Object> read() {
        long end = Instant.now().getEpochSecond() / 15 * 15;
        long start = end - 3600;
        String query = query();
        String url = base.toString().replaceAll("/+$", "") + "/api/v1/query_range?query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&start=" + start + "&end=" + end + "&step=15&timeout=5s&limit=128";
        try {
            var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(7))
                    .header("Accept", "application/json").GET().build();
            var pending = client.sendAsync(request, info -> new LimitedBodySubscriber());
            try {
                // Bound the entire body transfer, not just waiting for HTTP headers.
                var response = pending.get(7, TimeUnit.SECONDS);
                if (response.statusCode() != 200) throw unavailable();
                JsonNode result = mapper.readTree(response.body());
                JsonNode series = result.path("data").path("result");
                if (!"success".equals(result.path("status").asText())
                        || !"matrix".equals(result.path("data").path("resultType").asText())
                        || !series.isArray() || series.size() > 128) throw unavailable();
                for (JsonNode row : series) {
                    String signal = row.path("metric").path("signal").asText();
                    if ((!METRICS.containsKey(signal) && !"scrape_up".equals(signal))
                            || !row.path("values").isArray() || row.path("values").size() > 241) throw unavailable();
                }
                return Map.of("start", start, "end", end, "stepSeconds", 15, "windowSeconds", 300,
                        "query", query, "series", series, "warnings", result.has("warnings")
                                ? result.path("warnings") : List.of());
            } finally { if (!pending.isDone()) pending.cancel(true); }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Prometheus time-series unavailable. Check the server and scrape target.");
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long size;
        private boolean done;
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (done) return;
            for (var buffer : buffers) size += buffer.remaining();
            if (size > 2_000_000) {
                done = true; subscription.cancel();
                delegate.onError(new IllegalStateException("Prometheus response exceeds dashboard limit"));
            } else delegate.onNext(buffers);
        }
        @Override public void onError(Throwable error) { if (!done) { done = true; delegate.onError(error); } }
        @Override public void onComplete() { if (!done) { done = true; delegate.onComplete(); } }
    }
}
