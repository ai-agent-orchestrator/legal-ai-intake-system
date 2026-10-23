package com.ohgiraffers.handlermethod.dto;

import com.ohgiraffers.handlermethod.entity.IntakeDocument;
import java.time.LocalDateTime;

public record IntakeDocumentResponse(Long id, Long intakeId, String documentType, String originalFilename,
                                     String contentType, Long sizeBytes, LocalDateTime createdAt, String downloadUrl) {
    public static IntakeDocumentResponse from(IntakeDocument document) {
        return new IntakeDocumentResponse(document.getId(), document.getIntakeId(), document.getDocumentType(),
                document.getOriginalFilename(), document.getContentType(), document.getSizeBytes(), document.getCreatedAt(),
                "/api/intakes/" + document.getIntakeId() + "/documents/" + document.getId() + "/download");
    }
}
