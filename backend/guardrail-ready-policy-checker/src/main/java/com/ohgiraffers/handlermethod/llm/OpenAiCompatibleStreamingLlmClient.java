package com.ohgiraffers.handlermethod.llm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Component
public class OpenAiCompatibleStreamingLlmClient implements StreamingLlmClient {
    private static final ScheduledThreadPoolExecutor DEADLINES = deadlines();
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl, apiKey, model;
    private final int maxOutputTokens;
    private final long timeoutMs;

    public OpenAiCompatibleStreamingLlmClient(ObjectMapper objectMapper,
            @Value("${llm.base-url}") String baseUrl, @Value("${llm.api-key}") String apiKey,
            @Value("${llm.model}") String model, @Value("${llm.max-output-tokens}") int maxOutputTokens,
            @Value("${llm.request-timeout-ms}") long timeoutMs) {
        if (timeoutMs <= 0) throw new IllegalArgumentException("LLM timeout must be positive");
        this.objectMapper = objectMapper; this.baseUrl = baseUrl; this.apiKey = apiKey;
        this.model = model; this.maxOutputTokens = maxOutputTokens; this.timeoutMs = timeoutMs;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
    }

    @Override
    public void stream(String systemPrompt, String userMessage, Consumer<String> onChunk,
                       Consumer<Usage> onComplete, Consumer<Throwable> onError) {
        stream(systemPrompt, userMessage, onChunk, onComplete, onError, new LlmStreamControl());
    }

    @Override
    public void stream(String systemPrompt, String userMessage, Consumer<String> onChunk,
                       Consumer<Usage> onComplete, Consumer<Throwable> onError, LlmStreamControl control) {
        var listener = new Lines(control, onChunk, onComplete, onError);
        control.onCancel(() -> listener.fail(new CancellationException("LLM stream cancelled")));
        try {
            if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("LLM_API_KEY is not configured");
            String body = objectMapper.writeValueAsString(new ChatCompletionRequest(model, maxOutputTokens, true,
                    new StreamOptions(true), List.of(new Message("system", systemPrompt), new Message("user", userMessage))));
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .timeout(Duration.ofMillis(timeoutMs)).header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json").header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            boolean started = control.dispatch(() -> {
                // Keep the original future: cancelling only a dependent future need not cancel HTTP.
                var future = httpClient.sendAsync(request, info -> {
                    if (info.statusCode() < 200 || info.statusCode() >= 300) {
                        listener.fail(new IllegalStateException("LLM streaming HTTP " + info.statusCode()));
                        return HttpResponse.BodySubscribers.replacing((Void) null);
                    }
                    return HttpResponse.BodySubscribers.fromLineSubscriber(listener);
                });
                control.onCancel(() -> future.cancel(true));
                future.whenComplete((response, error) -> {
                    if (error != null) listener.fail(error.getCause() == null ? error : error.getCause());
                });
            });
            if (started && !control.cancelled()) {
                var deadline = DEADLINES.schedule(() -> listener.fail(new HttpTimeoutException("LLM stream deadline exceeded")),
                        timeoutMs, TimeUnit.MILLISECONDS);
                control.onCancel(() -> deadline.cancel(false));
            }
        } catch (Exception error) { listener.fail(error); }
    }

    private final class Lines implements Flow.Subscriber<String> {
        private final LlmStreamControl control;
        private final Consumer<String> chunk;
        private final Consumer<Usage> complete;
        private final Consumer<Throwable> error;
        private final AtomicBoolean finished = new AtomicBoolean();
        private final StringBuilder data = new StringBuilder();
        private Flow.Subscription subscription;
        Lines(LlmStreamControl control, Consumer<String> chunk, Consumer<Usage> complete, Consumer<Throwable> error) {
            this.control = control; this.chunk = chunk; this.complete = complete; this.error = error;
        }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            control.onCancel(subscription::cancel);
            if (!control.cancelled()) subscription.request(1);
        }
        @Override public void onNext(String line) {
            if (finished.get() || control.cancelled()) return;
            try {
                if (line.isEmpty()) { frame(); }
                else if (line.startsWith("data:")) {
                    String part = line.substring(5);
                    if (part.startsWith(" ")) part = part.substring(1);
                    if (!data.isEmpty()) data.append('\n');
                    data.append(part);
                }
                if (!finished.get() && !control.cancelled()) subscription.request(1);
            } catch (Exception failure) { fail(failure); }
        }
        private void frame() {
            if (data.isEmpty()) return;
            String event = data.toString().trim(); data.setLength(0);
            if ("[DONE]".equals(event)) {
                if (finished.compareAndSet(false, true)) {
                    Usage usage = control.snapshot().usage();
                    control.cancel();
                    complete.accept(usage == null ? new Usage(model, 0, 0, false) : usage);
                }
                return;
            }
            JsonNode payload = objectMapper.readTree(event);
            if (payload.hasNonNull("error")) throw new IllegalStateException("LLM provider stream error");
            JsonNode usage = payload.path("usage");
            JsonNode prompt = usage.path("prompt_tokens"), completion = usage.path("completion_tokens");
            if (prompt.isIntegralNumber() && prompt.canConvertToInt() && prompt.asInt() >= 0
                    && completion.isIntegralNumber() && completion.canConvertToInt() && completion.asInt() >= 0) {
                control.reportUsage(new Usage(model, prompt.asInt(), completion.asInt()));
            }
            JsonNode delta = payload.path("choices").path(0).path("delta").path("content");
            if (delta.isString() && !delta.asText().isEmpty()) chunk.accept(delta.asText());
        }
        @Override public void onError(Throwable failure) { fail(failure); }
        @Override public void onComplete() {
            // EOF alone is not proof that a provider finished successfully.
            if (!finished.get()) fail(new IllegalStateException("LLM stream ended before [DONE]"));
        }
        void fail(Throwable failure) {
            if (!finished.compareAndSet(false, true)) return;
            control.cancel();
            error.accept(failure);
        }
    }

    private static ScheduledThreadPoolExecutor deadlines() {
        var timer = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "llm-stream-deadline"); thread.setDaemon(true); return thread;
        });
        timer.setRemoveOnCancelPolicy(true);
        return timer;
    }
    private record ChatCompletionRequest(String model, int max_completion_tokens, boolean stream,
                                         StreamOptions stream_options, List<Message> messages) { }
    private record StreamOptions(boolean include_usage) { }
    private record Message(String role, String content) { }
}
