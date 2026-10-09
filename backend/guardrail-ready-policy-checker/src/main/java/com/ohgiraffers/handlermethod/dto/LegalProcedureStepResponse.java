package com.ohgiraffers.handlermethod.dto;

import com.ohgiraffers.handlermethod.entity.LegalProcedureStep;

import java.util.Arrays;
import java.util.List;

public record LegalProcedureStepResponse(
        int stepOrder,
        String stepCode,
        String title,
        String description,
        List<String> checklist
) {

    public static LegalProcedureStepResponse from(LegalProcedureStep step) {
        return new LegalProcedureStepResponse(
                step.getStepOrder(),
                step.getStepCode(),
                step.getTitle(),
                step.getDescription(),
                splitChecklist(step.getChecklist())
        );
    }

    private static List<String> splitChecklist(String checklist) {
        if (checklist == null || checklist.isBlank()) {
            return List.of();
        }
        return Arrays.stream(checklist.split("\\|"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
