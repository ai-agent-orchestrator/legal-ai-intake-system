package com.ohgiraffers.handlermethod.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ConsultationMemoCreateRequest(
        @NotBlank @Size(max = 4000) String content,
        @Size(max = 4)
        List<@NotBlank @Pattern(regexp = "REQUEST_DOCUMENTS|SCHEDULE_CONSULTATION|DOCUMENT_REVIEW|E_LITIGATION_GUIDE") String> followUpItems,
        /* 화면07이 상세를 불러올 때 받은 version. 보내면 그 사이 다른 상담자가 수정한 경우 409로 막는다. */
        @PositiveOrZero Long expectedVersion
) {
    public ConsultationMemoCreateRequest(String content, List<String> followUpItems) {
        this(content, followUpItems, null);
    }
}
