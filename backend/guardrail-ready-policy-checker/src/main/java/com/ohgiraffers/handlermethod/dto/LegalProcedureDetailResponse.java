package com.ohgiraffers.handlermethod.dto;

import com.ohgiraffers.handlermethod.entity.LegalProcedure;

import java.util.List;

public record LegalProcedureDetailResponse(
        String code,
        String domain,
        String title,
        String summary,
        String userRole,
        List<LegalProcedureStepResponse> steps,
        List<LegalFormTemplateResponse> forms,
        List<String> knowledgeGraphEdges
) {

    public static LegalProcedureDetailResponse of(LegalProcedure procedure,
                                                  List<LegalProcedureStepResponse> steps,
                                                  List<LegalFormTemplateResponse> forms) {
        return new LegalProcedureDetailResponse(
                procedure.getCode(),
                procedure.getDomain().name(),
                procedure.getTitle(),
                procedure.getSummary(),
                procedure.getUserRole(),
                steps,
                forms,
                List.of(
                        procedure.getCode() + " -> has_steps -> legal_procedure_step",
                        procedure.getCode() + " -> requires_forms -> legal_form_template",
                        procedure.getCode() + " -> belongs_to_domain -> " + procedure.getDomain().name()
                )
        );
    }
}
