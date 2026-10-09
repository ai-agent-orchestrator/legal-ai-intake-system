package com.ohgiraffers.handlermethod.riskengine;

public record FixedGuardResult(
        boolean allowed,
        String reasonCode,
        String safeResponse
) {

    public static FixedGuardResult allow() {
        return new FixedGuardResult(true, "ALLOWED", null);
    }

    public static FixedGuardResult block(String reasonCode, String safeResponse) {
        return new FixedGuardResult(false, reasonCode, safeResponse);
    }
}
