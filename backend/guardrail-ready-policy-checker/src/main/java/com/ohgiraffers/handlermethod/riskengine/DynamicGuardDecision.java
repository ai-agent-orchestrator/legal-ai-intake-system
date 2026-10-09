package com.ohgiraffers.handlermethod.riskengine;

public record DynamicGuardDecision(
        boolean blocked,
        String reasonCode,
        int riskScore,
        String safeResponse
) {

    public static DynamicGuardDecision allow(int riskScore) {
        return new DynamicGuardDecision(false, "DYNAMIC_ALLOWED", riskScore, null);
    }

    public static DynamicGuardDecision block(String reasonCode, int riskScore, String safeResponse) {
        return new DynamicGuardDecision(true, reasonCode, riskScore, safeResponse);
    }
}
