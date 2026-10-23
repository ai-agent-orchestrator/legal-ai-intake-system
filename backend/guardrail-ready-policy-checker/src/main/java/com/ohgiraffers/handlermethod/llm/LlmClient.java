package com.ohgiraffers.handlermethod.llm;

public interface LlmClient {

    LlmResponse chat(String systemPrompt, String userMessage);

    record LlmResponse(
            String content,
            String model,
            int promptTokens,
            int completionTokens,
            boolean usageAvailable
    ) {
        public LlmResponse {
            usageAvailable = usageAvailable && promptTokens >= 0 && completionTokens >= 0;
        }

        public LlmResponse(String content, String model, int promptTokens, int completionTokens) {
            this(content, model, promptTokens, completionTokens, true);
        }

        public int totalTokens() {
            return promptTokens + completionTokens;
        }
    }
}
