package com.ohgiraffers.handlermethod.guardrail;

/** Optional adapter response: usage contains aggregate token counts for this check. */
public record GuardrailUsage(Number prompt_tokens, Number completion_tokens) {
    public boolean available() {
        return valid(prompt_tokens) && valid(completion_tokens);
    }

    private static boolean valid(Number number) {
        return (number instanceof Integer || number instanceof Long || number instanceof java.math.BigInteger)
                && new java.math.BigDecimal(number.toString()).signum() >= 0
                && new java.math.BigDecimal(number.toString()).compareTo(java.math.BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0;
    }
}
