package com.ohgiraffers.handlermethod.risk;

import java.util.Arrays;
import java.util.List;

public enum AgentRiskPolicyRule {
    POLICY_VIOLATION(List.of("delete", "customer records", "sensitive", "blocked")),
    TOOL_ERROR(List.of("failing tool", "tool error", "unstable")),
    EXTERNAL_API_CALL(List.of("external", "external api", "upload")),
    APPROVAL_REQUIRED(List.of("email", "send customer report", "approval")),
    DB_WRITE(List.of("database", "db"));

    private final List<String> keywords;

    AgentRiskPolicyRule(List<String> keywords) {
        this.keywords = keywords;
    }

    public boolean matches(String... values) {
        return Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .map(String::toLowerCase)
                .anyMatch(this::containsKeyword);
    }

    private boolean containsKeyword(String value) {
        return keywords.stream().anyMatch(value::contains);
    }
}
