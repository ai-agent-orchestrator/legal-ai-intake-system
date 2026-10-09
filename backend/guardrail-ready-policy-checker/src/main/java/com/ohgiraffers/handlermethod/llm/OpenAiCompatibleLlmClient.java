package com.ohgiraffers.handlermethod.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final int maxOutputTokens;

    public OpenAiCompatibleLlmClient(
            @Value("${llm.base-url}") String baseUrl,
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.model}") String model,
            @Value("${llm.max-output-tokens}") int maxOutputTokens,
            @Value("${llm.request-timeout-ms}") long timeoutMs
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey;
        this.model = model;
        this.maxOutputTokens = maxOutputTokens;
    }

    @Override
    public LlmResponse chat(String systemPrompt, String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("LLM_API_KEY is not configured");
        }

        ChatCompletionResponse response = restClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ChatCompletionRequest(
                        model,
                        maxOutputTokens,
                        List.of(
                                new Message("system", systemPrompt),
                                new Message("user", userMessage)
                        )
                ))
                .retrieve()
                .body(ChatCompletionResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message() == null
                || response.choices().getFirst().message().content() == null
                || response.choices().getFirst().message().content().isBlank()) {
            throw new IllegalStateException("LLM returned an empty response");
        }

        Usage usage = response.usage();
        return new LlmResponse(
                response.choices().getFirst().message().content(),
                response.model() == null ? model : response.model(),
                usage == null ? 0 : usage.prompt_tokens(),
                usage == null ? 0 : usage.completion_tokens()
        );
    }

    private record ChatCompletionRequest(
            String model,
            int max_completion_tokens,
            List<Message> messages
    ) {
    }

    private record Message(String role, String content) {
    }

    private record ChatCompletionResponse(
            String model,
            List<Choice> choices,
            Usage usage
    ) {
    }

    private record Choice(Message message) {
    }

    private record Usage(int prompt_tokens, int completion_tokens) {
    }
}
