package com.ohgiraffers.handlermethod.support;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 409 Conflict with a machine-readable code and the current server state,
 * so the screen can refresh or explain the conflict instead of guessing.
 */
public final class ApiConflictException extends RuntimeException {
    private final String code;
    private final Map<String, Object> details;

    public ApiConflictException(String code, String message, Map<String, ?> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public String getCode() { return code; }
    public Map<String, Object> getDetails() { return details; }
}
