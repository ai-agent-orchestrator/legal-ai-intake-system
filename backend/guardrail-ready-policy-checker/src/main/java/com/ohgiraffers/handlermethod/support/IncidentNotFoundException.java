package com.ohgiraffers.handlermethod.support;

public final class IncidentNotFoundException extends RuntimeException {
    public IncidentNotFoundException() { super("Incident not found"); }
}
