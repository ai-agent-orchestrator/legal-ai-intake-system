package com.ohgiraffers.handlermethod.guardrail;

public record InputGuardrailResult(
        String status,
        String content,
        String rail
) {

    public boolean allowed() {
        return "PASSED".equals(status) || "MODIFIED".equals(status);
    }
}
