package com.ohgiraffers.handlermethod.legal;

public enum IntakeStatus {
    SUBMITTED,
    REVIEWED,
    NEED_MORE_INFO,
    CONSULTATION_READY,
    CLOSED;

    public boolean canChangeTo(IntakeStatus next) {
        return this == next || (this != CLOSED && next != SUBMITTED);
    }
}
