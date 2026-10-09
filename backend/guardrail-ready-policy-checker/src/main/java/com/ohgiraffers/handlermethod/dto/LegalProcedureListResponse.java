package com.ohgiraffers.handlermethod.dto;

import com.ohgiraffers.handlermethod.entity.LegalProcedure;

public record LegalProcedureListResponse(
        String code,
        String domain,
        String title,
        String summary,
        String userRole
) {

    public static LegalProcedureListResponse from(LegalProcedure procedure) {
        return new LegalProcedureListResponse(
                procedure.getCode(),
                procedure.getDomain().name(),
                procedure.getTitle(),
                procedure.getSummary(),
                procedure.getUserRole()
        );
    }
}
