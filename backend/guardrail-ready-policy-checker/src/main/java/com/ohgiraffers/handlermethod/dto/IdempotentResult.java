package com.ohgiraffers.handlermethod.dto;

/**
 * 재전송 요청이면 replayed=true. body는 처음 저장된 같은 대상(같은 id)을 다시 조회한 값이다
 * (문의: 현재 상세, 메모: 처음 저장한 메모 + 현재 접수 version). 최초 응답 스냅샷을 보관하지는 않는다.
 */
public record IdempotentResult<T>(T body, boolean replayed) {
    public static <T> IdempotentResult<T> created(T body) { return new IdempotentResult<>(body, false); }
    public static <T> IdempotentResult<T> replayed(T body) { return new IdempotentResult<>(body, true); }
}
