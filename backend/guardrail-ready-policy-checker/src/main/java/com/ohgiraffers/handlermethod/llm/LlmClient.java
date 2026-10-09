package com.ohgiraffers.handlermethod.llm;

public interface LlmClient {

    LlmResponse chat(String systemPrompt, String userMessage);

    record LlmResponse(
            String content,
            String model,
            int promptTokens,
            int completionTokens
    ) {
        public int totalTokens() {
            return promptTokens + completionTokens;
        }
    }
}
