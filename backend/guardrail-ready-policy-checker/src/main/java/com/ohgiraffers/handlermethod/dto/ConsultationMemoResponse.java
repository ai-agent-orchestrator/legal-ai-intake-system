package com.ohgiraffers.handlermethod.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ConsultationMemoResponse(
        Long id,
        Long intakeId,
        String contentMasked,
        List<String> followUpItems,
        LocalDateTime createdAt,
        String createdBy
) {
}
