package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.IntakeEventRequest;
import com.ohgiraffers.handlermethod.dto.IntakeEventResponse;
import com.ohgiraffers.handlermethod.entity.IntakeEvent;
import com.ohgiraffers.handlermethod.legal.IntakeEventType;
import com.ohgiraffers.handlermethod.repository.IntakeEventRepository;
import com.ohgiraffers.handlermethod.repository.LegalIntakeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@Service
public class IntakeEventService {
    private final IntakeEventRepository events;
    private final LegalIntakeRepository intakes;

    public IntakeEventService(IntakeEventRepository events, LegalIntakeRepository intakes) {
        this.events = events;
        this.intakes = intakes;
    }

    public IntakeEventResponse record(IntakeEventRequest request) {
        String eventId = request.eventId().trim();
        String sessionId = request.sessionId().trim();
        if (request.eventType() == IntakeEventType.SUMMARY_VIEWED && request.intakeId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SUMMARY_VIEWED requires intakeId");
        }
        if (request.intakeId() != null) {
            var intake = intakes.findById(request.intakeId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intake not found"));
            if (!sessionId.equals(intake.getSessionId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId does not match intake");
            }
        }
        var existing = events.findById(eventId);
        if (existing.isPresent()) { return duplicate(existing.get(), request, sessionId); }
        IntakeEvent event = new IntakeEvent(eventId, request.eventType(), sessionId, request.intakeId());
        try {
            // A new entity has a null version: Spring Data persists it instead of merging a retry.
            events.saveAndFlush(event);
            return IntakeEventResponse.from(event, true);
        } catch (DataIntegrityViolationException exception) {
            return events.findById(eventId).map(saved -> duplicate(saved, request, sessionId)).orElseThrow(() -> exception);
        }
    }

    private IntakeEventResponse duplicate(IntakeEvent event, IntakeEventRequest request, String sessionId) {
        if (event.getEventType() != request.eventType() || !event.getSessionId().equals(sessionId)
                || !Objects.equals(event.getIntakeId(), request.intakeId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "eventId already has a different payload");
        }
        return IntakeEventResponse.from(event, false);
    }
}
