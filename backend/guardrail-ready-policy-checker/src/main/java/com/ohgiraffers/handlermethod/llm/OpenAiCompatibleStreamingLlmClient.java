package com.ohgiraffers.handlermethod.llm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Component
public class OpenAiCompatibleStreamingLlmClient implements StreamingLlmClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int maxOutputTokens;

    public OpenAiCompatibleStreamingLlmClient(
            ObjectMapper objectMapper,
            @Value("${llm.base-url}") String baseUrl,
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.model}") String model,
            @Value("${llm.max-output-tokens}") int maxOutputTokens,
            @Value("${llm.request-timeout-ms}") long timeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.maxOutputTokens = maxOutputTokens;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @Override
    public void stream(
            String systemPrompt,
            String userMessage,
            Consumer<String> onChunk,
            Consumer<Usage> onComplete,
            Consumer<Throwable> onError
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            onError.accept(new IllegalStateException("LLM_API_KEY is not configured"));
            return;
        }

        try {
            String body = objectMapper.writeValueAsString(new ChatCompletionRequest(
                    model,
                    maxOutputTokens,
                    true,
                    new StreamOptions(true),
                    List.of(
                            new Message("system", systemPrompt),
                            new Message("user", userMessage)
                    )
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .timeout(Duration.ofMillis(30_000))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(response -> consume(response, onChunk, onComplete, onError))
                    .exceptionally(error -> {
                        onError.accept(error.getCause() == null ? error : error.getCause());
                        return null;
                    });
        } catch (Exception exception) {
            onError.accept(exception);
        }
    }

    private void consume(
            HttpResponse<Stream<String>> response,
            Consumer<String> onChunk,
            Consumer<Usage> onComplete,
            Consumer<Throwable> onError
    ) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            onError.accept(new IllegalStateException("LLM streaming request failed: HTTP " + response.statusCode()));
            return;
        }

        int promptTokens = 0;
        int completionTokens = 0;

        try (Stream<String> lines = response.body()) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (!line.startsWith("data:")) {
                    continue;
                }

                String data = line.substring("data:".length()).trim();
                if ("[DONE]".equals(data)) {
                    onComplete.accept(new Usage(model, promptTokens, completionTokens));
                    return;
                }

                JsonNode payload = objectMapper.readTree(data);
                JsonNode delta = payload.path("choices").path(0).path("delta").path("content");
                if (!delta.isMissingNode() && !delta.isNull() && !delta.asText().isBlank()) {
                    onChunk.accept(delta.asText());
                }

                JsonNode usage = payload.path("usage");
                if (!usage.isMissingNode() && !usage.isNull()) {
                    promptTokens = usage.path("prompt_tokens").asInt(promptTokens);
                    completionTokens = usage.path("completion_tokens").asInt(completionTokens);
                }
            }

            onComplete.accept(new Usage(model, promptTokens, completionTokens));
        } catch (RuntimeException exception) {
            onError.accept(exception);
        }
    }

    private record ChatCompletionRequest(
            String model,
            int max_completion_tokens,
            boolean stream,
            StreamOptions stream_options,
            List<Message> messages
    ) {
    }

    private record StreamOptions(boolean include_usage) {
    }

    private record Message(String role, String content) {
    }
}
