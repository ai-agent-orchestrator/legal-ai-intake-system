package com.ohgiraffers.handlermethod.dto;

public record AiStreamEvent(
        String traceId,
        String text,
        boolean blocked,
        boolean fallback,
        String model,
        String reason,
        Integer promptTokens,
        Integer completionTokens,
        String reasonCode,
        String detailCode,
        Boolean usageAvailable,
        String costSource
) {

    public AiStreamEvent withCost(Integer promptTokens, Integer completionTokens, boolean available, String source) {
        return new AiStreamEvent(traceId, text, blocked, fallback, model, reason, promptTokens, completionTokens,
                reasonCode, detailCode, available, source);
    }

    public static AiStreamEvent token(String traceId, String model, String text) {
        return new AiStreamEvent(traceId, text, false, false, model, null,
                null, null, null, null, null, null);
    }

    public static AiStreamEvent buffered(String traceId, String model) {
        return new AiStreamEvent(traceId, null, false, false, model,
                "OUTPUT_RAIL_BUFFERED", null, null, null, null, null, null);
    }

    public static AiStreamEvent terminal(
            String traceId,
            String model,
            int promptTokens,
            int completionTokens
    ) {
        return terminal(traceId, model, promptTokens, completionTokens, true, "REPORTED");
    }

    public static AiStreamEvent terminal(String traceId, String model, Integer promptTokens,
                                         Integer completionTokens, boolean usageAvailable, String costSource) {
        return new AiStreamEvent(traceId, null, false, false, model, null,
                promptTokens, completionTokens, null, null, usageAvailable, costSource);
    }

    public static AiStreamEvent blocked(String traceId, String model, String reason) {
        return blocked(traceId, model, "해당 답변은 안전 정책에 따라 중단되었습니다.",
                reason, reason, null);
    }

    public static AiStreamEvent blocked(String traceId, String model, String text,
                                        String legacyReason, String reasonCode, String detailCode) {
        return new AiStreamEvent(traceId, text, true, true, model,
                legacyReason, null, null, reasonCode, detailCode, null, null);
    }

    public static AiStreamEvent fallback(String traceId, String model, String reason) {
        return new AiStreamEvent(traceId,
                "현재 요청을 안전하게 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.",
                false, true, model, reason, null, null, reason, null, null, null);
    }
}
