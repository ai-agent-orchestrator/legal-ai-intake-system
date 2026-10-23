package com.ohgiraffers.handlermethod.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record LegalIntakeStatusRequest(
        @NotBlank
        @Pattern(regexp = "SUBMITTED|REVIEWED|NEED_MORE_INFO|CONSULTATION_READY|CLOSED")
        String status,
        /* 화면07이 상세를 불러올 때 받은 version. 생략하면 기존처럼 버전 검사 없이 처리한다. */
        @PositiveOrZero Long expectedVersion
) {
    public LegalIntakeStatusRequest(String status) {
        this(status, null);
    }
}
