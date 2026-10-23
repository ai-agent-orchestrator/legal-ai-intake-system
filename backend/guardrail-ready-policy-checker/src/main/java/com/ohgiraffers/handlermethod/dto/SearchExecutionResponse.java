package com.ohgiraffers.handlermethod.dto;

import com.ohgiraffers.handlermethod.entity.AiSearchRequest;
import java.time.LocalDateTime;

public record SearchExecutionResponse(String id, String stage, String provider, String errorCode,
                                      LocalDateTime startedAt, LocalDateTime finishedAt, String sourceWarningCode) {
    public static SearchExecutionResponse from(AiSearchRequest request) {
        return request.getExecutionId() == null || request.getExecutionStartedAt() == null ? null :
                new SearchExecutionResponse(request.getExecutionId(), request.getExecutionStage(), "NAVER_WEB",
                        request.getExecutionError(), request.getExecutionStartedAt(), request.getExecutionFinishedAt(), request.getSourceWarningCode());
    }
}
