package com.ohgiraffers.handlermethod.dto;

import java.util.List;

public record LegalChecklistResponse(
        String domain,
        String role,
        String stageCode,
        String stageLabel,
        List<String> checklist,
        String recommendedAction
) {
}
