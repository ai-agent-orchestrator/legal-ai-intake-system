package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.AiSearchRequestResponse;
import com.ohgiraffers.handlermethod.service.IntakeAccessService;
import com.ohgiraffers.handlermethod.service.LegalIntakeRecordService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class IntakeSearchHistoryController {
    private final IntakeAccessService access;
    private final LegalIntakeRecordService records;

    public IntakeSearchHistoryController(IntakeAccessService access, LegalIntakeRecordService records) {
        this.access = access;
        this.records = records;
    }

    @GetMapping("/api/intakes/{intakeId}/search-requests")
    public List<AiSearchRequestResponse> history(@PathVariable Long intakeId, Authentication authentication) {
        access.requireAccess(intakeId, authentication);
        return records.findSearchRequests(intakeId, authentication);
    }
}
