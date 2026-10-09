package com.ohgiraffers.handlermethod.dto;

public record AiChatResponse(
        String answer,
        boolean blocked,
        boolean fallback,
        String traceId,
        String model,
        String reason
) {

    public static AiChatResponse success(String answer, String traceId, String model) {
        return new AiChatResponse(answer, false, false, traceId, model, null);
    }

    public static AiChatResponse fallback(String answer, String traceId, String model, String reason) {
        return new AiChatResponse(answer, true, true, traceId, model, reason);
    }

    public static AiChatResponse technicalFallback(String answer, String traceId, String model, String reason) {
        return new AiChatResponse(answer, false, true, traceId, model, reason);
    }
}
