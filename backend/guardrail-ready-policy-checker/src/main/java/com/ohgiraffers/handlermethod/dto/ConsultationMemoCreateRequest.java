package com.ohgiraffers.handlermethod.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ConsultationMemoCreateRequest(
        @NotBlank @Size(max = 4000) String content,
        @Size(max = 4)
        List<@NotBlank @Pattern(regexp = "REQUEST_DOCUMENTS|SCHEDULE_CONSULTATION|DOCUMENT_REVIEW|E_LITIGATION_GUIDE") String> followUpItems
) {
}
