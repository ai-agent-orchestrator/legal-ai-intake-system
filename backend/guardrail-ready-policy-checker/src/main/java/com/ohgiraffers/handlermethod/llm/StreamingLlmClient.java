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

    default void stream(String systemPrompt, String userMessage, Consumer<String> onChunk,
                        Consumer<Usage> onComplete, Consumer<Throwable> onError, LlmStreamControl control) {
        control.dispatch(() -> stream(systemPrompt, userMessage, onChunk, usage -> {
            control.reportUsage(usage);
            onComplete.accept(usage);
        }, onError));
    }

    record Usage(String model, int promptTokens, int completionTokens, boolean usageAvailable) {
        public Usage {
            usageAvailable = usageAvailable && promptTokens >= 0 && completionTokens >= 0;
        }

        public Usage(String model, int promptTokens, int completionTokens) {
            this(model, promptTokens, completionTokens, true);
        }
    }
}
