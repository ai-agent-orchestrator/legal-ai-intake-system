package com.ohgiraffers.handlermethod.guardrail;

import com.ohgiraffers.handlermethod.dto.AgentRiskAnalyzeRequest;

public interface GuardrailDecisionClient {

    GuardrailDecisionResult check(AgentRiskAnalyzeRequest request);
}
