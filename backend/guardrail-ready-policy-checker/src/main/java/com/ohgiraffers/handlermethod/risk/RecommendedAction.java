package com.ohgiraffers.handlermethod.risk;

public enum RecommendedAction {
    ALLOW,
    REQUIRE_APPROVAL,
    BLOCK,
    BLOCK_AND_ESCALATE,
    DISABLE_TOOL_TEMPORARILY,
    CREATE_INCIDENT
}
