package com.ohgiraffers.handlermethod.riskengine;

import java.util.List;

public record PrivacyOutputGuardResult(
        String content,
        boolean masked,
        List<String> piiTypes
) {
}
