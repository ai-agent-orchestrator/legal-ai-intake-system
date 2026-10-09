package com.ohgiraffers.handlermethod.dto;

public record AiStreamEvent(
        String traceId,
        String text,
        boolean blocked,
        boolean fallback,
        String model,
        String reason,
        Integer promptTokens,
        Integer completionTokens
) {

    public static AiStreamEvent token(String traceId, String model, String text) {
        return new AiStreamEvent(traceId, text, false, false, model, null, null, null);
    }

    public static AiStreamEvent terminal(
            String traceId,
            String model,
            int promptTokens,
            int completionTokens
    ) {
        return new AiStreamEvent(traceId, null, false, false, model, null,
                promptTokens, completionTokens);
    }

    public static AiStreamEvent blocked(String traceId, String model, String reason) {
        return new AiStreamEvent(traceId,
                "해당 답변은 안전 정책에 따라 중단되었습니다.",
                true, true, model, reason, null, null);
    }

    public static AiStreamEvent fallback(String traceId, String model, String reason) {
        return new AiStreamEvent(traceId,
                "현재 요청을 안전하게 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.",
                false, true, model, reason, null, null);
    }
}
