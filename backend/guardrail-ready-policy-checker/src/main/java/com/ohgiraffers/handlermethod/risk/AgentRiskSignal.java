package com.ohgiraffers.handlermethod.risk;

public enum AgentRiskSignal {
    SAFE_REQUEST,
    POLICY_VIOLATION,
    RETRY,
    EXTERNAL_API_CALL,
    TOOL_ERROR,
    APPROVAL_REQUIRED,
    DB_WRITE
}
