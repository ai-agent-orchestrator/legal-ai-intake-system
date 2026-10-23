package com.ohgiraffers.handlermethod.dto;

public record AiChatResponse(
        String answer,
        boolean blocked,
        boolean fallback,
        String traceId,
        String model,
        String reason,
        String reasonCode,
        String detailCode,
        Boolean usageAvailable,
        String costSource
) {

    public static AiChatResponse success(String answer, String traceId, String model) {
        return new AiChatResponse(answer, false, false, traceId, model, null, null, null, null, null);
    }

    public static AiChatResponse success(String answer, String traceId, String model,
                                         boolean usageAvailable, String costSource) {
        return new AiChatResponse(answer, false, false, traceId, model,
                null, null, null, usageAvailable, costSource);
    }

    public static AiChatResponse fallback(String answer, String traceId, String model, String reason) {
        return fallback(answer, traceId, model, reason, reason, null);
    }

    public static AiChatResponse fallback(String answer, String traceId, String model,
                                          String legacyReason, String reasonCode, String detailCode) {
        return new AiChatResponse(answer, true, true, traceId, model,
                legacyReason, reasonCode, detailCode, null, null);
    }

    public static AiChatResponse technicalFallback(String answer, String traceId, String model, String reason) {
        return new AiChatResponse(answer, false, true, traceId, model, reason, reason, null, null, null);
    }
}
