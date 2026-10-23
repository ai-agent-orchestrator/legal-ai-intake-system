package com.ohgiraffers.handlermethod.incident;

/*
 * 화면12 Incident 상태 전이 규칙.
 *   OPEN -> ACKNOWLEDGED -> RESOLVED, OPEN -> RESOLVED
 * 같은 상태로의 요청은 변경 없음(재전송 허용). RESOLVED(종결)는 다시 열리지 않는다.
 */
public enum IncidentStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED;

    public boolean canChangeTo(IncidentStatus next) {
        return this == next || next.ordinal() > ordinal();
    }

    public boolean isTerminal() {
        return this == RESOLVED;
    }
}
