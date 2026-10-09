package com.ohgiraffers.handlermethod.guardrail;

public interface OutputGuardrailClient {

    OutputGuardrailResult check(String response);
}
