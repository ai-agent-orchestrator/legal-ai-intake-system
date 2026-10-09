package com.ohgiraffers.handlermethod.guardrail;

import java.util.List;

public record GuardrailDecisionResult(
        String decision,
        List<String> signals,
        String recommendedAction,
        boolean guardrailReady
) {
}
