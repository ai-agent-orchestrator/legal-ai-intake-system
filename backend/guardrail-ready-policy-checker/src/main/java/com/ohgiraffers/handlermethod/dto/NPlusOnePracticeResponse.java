package com.ohgiraffers.handlermethod.dto;

import com.ohgiraffers.handlermethod.support.TraceContext;

import java.util.List;

public record NPlusOnePracticeResponse(
        String mode,
        String message,
        int count,
        String traceId,
        List<NPlusOneChatLogResponse> chatLogs
) {

    public static NPlusOnePracticeResponse of(String mode,
                                              String message,
                                              List<NPlusOneChatLogResponse> chatLogs) {
        return new NPlusOnePracticeResponse(
                mode,
                message,
                chatLogs.size(),
                TraceContext.currentTraceId(),
                chatLogs
        );
    }
}
