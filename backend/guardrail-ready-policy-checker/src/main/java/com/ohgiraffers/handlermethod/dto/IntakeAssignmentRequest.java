package com.ohgiraffers.handlermethod.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record IntakeAssignmentRequest(
        @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9_.-]+") String assignedLawyerUsername
) {}
