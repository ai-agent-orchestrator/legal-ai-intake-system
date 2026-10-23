package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.LegalIntakeStatusRequest;
import com.ohgiraffers.handlermethod.dto.LegalIntakeStatusResponse;
import com.ohgiraffers.handlermethod.service.LegalIntakeRecordService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/intakes")
public class LegalIntakeStatusController {
    private final LegalIntakeRecordService service;

    public LegalIntakeStatusController(LegalIntakeRecordService service) {
        this.service = service;
    }

    @PatchMapping("/{id}/status")
    public LegalIntakeStatusResponse update(@PathVariable Long id, @Valid @RequestBody LegalIntakeStatusRequest request, Authentication authentication) {
        return service.updateStatus(id, request, authentication);
    }
}
