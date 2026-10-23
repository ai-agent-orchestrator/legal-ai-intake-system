package com.ohgiraffers.handlermethod.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

public record ConsultationMemoResponse(
        Long id,
        Long intakeId,
        String contentMasked,
        List<String> followUpItems,
        LocalDateTime createdAt,
        String createdBy,
        /* 메모 저장 직후의 접수 version. 화면07은 이 값으로 다음 수정 요청을 보낸다. */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long intakeVersion
) {
    public ConsultationMemoResponse(Long id, Long intakeId, String contentMasked, List<String> followUpItems,
                                    LocalDateTime createdAt, String createdBy) {
        this(id, intakeId, contentMasked, followUpItems, createdAt, createdBy, null);
    }
}
