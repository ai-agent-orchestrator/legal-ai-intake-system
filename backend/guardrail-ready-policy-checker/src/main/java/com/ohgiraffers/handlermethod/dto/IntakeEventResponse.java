package com.ohgiraffers.handlermethod.dto;

import com.ohgiraffers.handlermethod.entity.IntakeEvent;
import com.ohgiraffers.handlermethod.legal.IntakeEventType;
import java.time.Instant;

public record IntakeEventResponse(String eventId, IntakeEventType eventType, String sessionId, Long intakeId,
                                  Instant receivedAt, boolean created) {
    public static IntakeEventResponse from(IntakeEvent event, boolean created) {
        return new IntakeEventResponse(event.getEventId(), event.getEventType(), event.getSessionId(),
                event.getIntakeId(), event.getReceivedAt(), created);
    }
}
