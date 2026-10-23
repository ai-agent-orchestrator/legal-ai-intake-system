package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.IntakeAssignmentRequest;
import com.ohgiraffers.handlermethod.dto.LegalIntakeResponse;
import com.ohgiraffers.handlermethod.service.LegalIntakeRecordService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/intakes")
public class IntakeAssignmentController {
    private final LegalIntakeRecordService records;
    public IntakeAssignmentController(LegalIntakeRecordService records) { this.records = records; }

    @PatchMapping("/{id}/assignment")
    public LegalIntakeResponse assign(@PathVariable Long id, @Valid @RequestBody IntakeAssignmentRequest request,
                                      Authentication authentication) {
        return records.assignLawyer(id, request.assignedLawyerUsername(), authentication);
    }
}
