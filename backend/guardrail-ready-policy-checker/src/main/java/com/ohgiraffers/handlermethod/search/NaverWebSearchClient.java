package com.ohgiraffers.handlermethod.search;

import com.ohgiraffers.handlermethod.riskengine.PrivacyOutputGuardService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

@Component
public class NaverWebSearchClient implements ExternalSearchClient {
    private final String baseUrl, clientId, secret;
    private final int limit;
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final PrivacyOutputGuardService privacy;

    public NaverWebSearchClient(ObjectMapper mapper, PrivacyOutputGuardService privacy,
            @Value("${search.naver.base-url:https://openapi.naver.com}") String baseUrl,
            @Value("${search.naver.client-id:}") String clientId,
            @Value("${search.naver.client-secret:}") String secret,
            @Value("${search.max-results:3}") int limit,
            @Value("${search.request-timeout-ms:10000}") long timeoutMs) {
        if (limit < 1 || limit > 5 || timeoutMs < 100 || timeoutMs > 60000) throw new IllegalArgumentException("Invalid search limits");
        URI base = URI.create(baseUrl);
        if (base.getHost() == null || base.getRawUserInfo() != null || base.getQuery() != null
                || !("https".equals(base.getScheme()) || ("http".equals(base.getScheme())
                && List.of("127.0.0.1", "localhost", "[::1]").contains(base.getHost())))) {
            throw new IllegalArgumentException("Search provider requires HTTPS (loopback HTTP allowed for tests)");
        }
        this.baseUrl = baseUrl; this.clientId = clientId; this.secret = secret;
        this.limit = limit; this.timeout = Duration.ofMillis(timeoutMs); this.mapper = mapper; this.privacy = privacy;
        http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override public boolean configured() { return !clientId.isBlank() && !secret.isBlank(); }

    @Override public List<Result> search(String query) {
        if (!configured()) throw new SearchFailure("SEARCH_NOT_CONFIGURED");
        if (query == null || query.isBlank() || query.length() > 300) throw new SearchFailure("INVALID_QUERY");
        URI uri = UriComponentsBuilder.fromUriString(baseUrl).path("/v1/search/webkr.json")
                .queryParam("query", "{query}").queryParam("display", limit).queryParam("start", 1)
                .encode().buildAndExpand(query).toUri();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("X-Naver-Client-Id", clientId).header("X-Naver-Client-Secret", secret)
                .header("Accept", "application/json").GET().build();
        try {
            HttpResponse<byte[]> response = http.send(request, info -> new LimitedBody());
                if (response.statusCode() != 200) throw new SearchFailure("SEARCH_PROVIDER_ERROR");
                var root = mapper.readTree(response.body());
                var items = root.get("items");
                if (items == null || !items.isArray()) throw new SearchFailure("SEARCH_RESPONSE_INVALID");
                List<Result> results = new ArrayList<>();
                var seen = new HashSet<String>();
                for (var item : items) {
                    String url = item.path("link").asString("").trim();
                    String title = clean(item.path("title").asString(""), 300);
                    if (title.isBlank() || !SearchText.publicUrl(url) || !seen.add(url)) continue;
                    results.add(new Result(title, url, clean(item.path("description").asString(""), 1000)));
                    if (results.size() == limit) break;
                }
                if (!items.isEmpty() && results.isEmpty()) throw new SearchFailure("SEARCH_RESPONSE_INVALID");
                return List.copyOf(results);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new SearchFailure("SEARCH_INTERRUPTED");
        } catch (SearchFailure exception) { throw exception;
        } catch (Exception exception) { throw new SearchFailure("SEARCH_PROVIDER_ERROR"); }
    }

    private String clean(String text, int limit) { return SearchText.plain(privacy.mask(SearchText.plain(text, limit)).content(), limit); }
    @PreDestroy public void close() { http.shutdownNow(); }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long received;
        private boolean failed;
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (failed) return;
            for (var buffer : buffers) received += buffer.remaining();
            if (received > 1_048_576) { failed = true; subscription.cancel(); delegate.onError(new SearchFailure("SEARCH_RESPONSE_INVALID")); }
            else delegate.onNext(buffers);
        }
        @Override public void onError(Throwable error) { if (!failed) delegate.onError(error); }
        @Override public void onComplete() { if (!failed) delegate.onComplete(); }
    }
}
