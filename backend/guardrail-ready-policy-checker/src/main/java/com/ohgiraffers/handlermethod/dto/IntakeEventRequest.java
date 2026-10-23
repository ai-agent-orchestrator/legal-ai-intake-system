package com.ohgiraffers.handlermethod.dto;

import com.ohgiraffers.handlermethod.legal.IntakeEventType;
import jakarta.validation.constraints.*;

public record IntakeEventRequest(@NotBlank @Size(max = 100) String eventId,
                                 @NotNull IntakeEventType eventType,
                                 @NotBlank @Size(max = 100) String sessionId,
                                 @Positive Long intakeId) { }
