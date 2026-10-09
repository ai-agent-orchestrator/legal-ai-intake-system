package com.ohgiraffers.handlermethod.dto;

import com.ohgiraffers.handlermethod.entity.LegalFormTemplate;

public record LegalFormTemplateResponse(
        String procedureCode,
        String formCode,
        String title,
        String fileType,
        String usageNote
) {

    public static LegalFormTemplateResponse from(LegalFormTemplate form) {
        return new LegalFormTemplateResponse(
                form.getProcedureCode(),
                form.getFormCode(),
                form.getTitle(),
                form.getFileType(),
                form.getUsageNote()
        );
    }
}
