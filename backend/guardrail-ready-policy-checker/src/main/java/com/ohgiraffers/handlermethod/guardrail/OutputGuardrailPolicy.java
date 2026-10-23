package com.ohgiraffers.handlermethod.guardrail;

/** Local checks must run before reserving or calling a paid output inspection. */
public final class OutputGuardrailPolicy {
    private OutputGuardrailPolicy() { }

    public static OutputGuardrailResult localBlock(String response) {
        if (response == null || response.isBlank() || response.length() > 12_000) {
            return new OutputGuardrailResult("BLOCKED", "답변이 비어 있거나 허용된 길이를 초과했습니다.",
                    "local output policy", new GuardrailUsage(0, 0));
        }
        return null;
    }
}
