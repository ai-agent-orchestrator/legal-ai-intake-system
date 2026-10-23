package com.ohgiraffers.handlermethod.support;

public final class AiSessionIds {

    public static final String ANONYMOUS = "anonymous";

    private AiSessionIds() {
    }

    // Requests without an ID intentionally share one application-local session.
    public static String normalize(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? ANONYMOUS : sessionId.trim();
    }
}
