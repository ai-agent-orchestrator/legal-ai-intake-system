package com.ohgiraffers.handlermethod.llm;

import java.util.function.Consumer;

public interface StreamingLlmClient {

    void stream(
            String systemPrompt,
            String userMessage,
            Consumer<String> onChunk,
            Consumer<Usage> onComplete,
            Consumer<Throwable> onError
    );

    record Usage(String model, int promptTokens, int completionTokens) {
    }
}
