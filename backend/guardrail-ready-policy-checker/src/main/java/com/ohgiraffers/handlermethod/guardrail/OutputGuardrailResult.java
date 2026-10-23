package com.ohgiraffers.handlermethod.guardrail;

public record OutputGuardrailResult(
        String status,
        String content,
        String rail,
        GuardrailUsage usage
) {
    public OutputGuardrailResult(String status, String content, String rail) {
        this(status, content, rail, null);
    }

    public boolean allowed() {
        return "PASSED".equals(status) || "MODIFIED".equals(status);
    }
}
