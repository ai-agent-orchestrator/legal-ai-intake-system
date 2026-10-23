package com.ohgiraffers.handlermethod.entity;

import com.ohgiraffers.handlermethod.legal.IntakeEventType;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "intake_event")
public class IntakeEvent {
    @Id @Column(length = 100) private String eventId;
    @Version private Long version;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private IntakeEventType eventType;
    @Column(nullable = false, length = 100) private String sessionId;
    private Long intakeId;
    @Column(nullable = false) private Instant receivedAt;

    protected IntakeEvent() { }

    public IntakeEvent(String eventId, IntakeEventType eventType, String sessionId, Long intakeId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.sessionId = sessionId;
        this.intakeId = intakeId;
        this.receivedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    public String getEventId() { return eventId; }
    public IntakeEventType getEventType() { return eventType; }
    public String getSessionId() { return sessionId; }
    public Long getIntakeId() { return intakeId; }
    public Instant getReceivedAt() { return receivedAt; }
}
