package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.ConsultationMemoCreateRequest;
import com.ohgiraffers.handlermethod.dto.ConsultationMemoResponse;
import com.ohgiraffers.handlermethod.service.LegalIntakeRecordService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/intakes")
public class ConsultationMemoController {
    private final LegalIntakeRecordService service;

    public ConsultationMemoController(LegalIntakeRecordService service) {
        this.service = service;
    }

    @PostMapping("/{id}/memo")
    @ResponseStatus(HttpStatus.CREATED)
    public ConsultationMemoResponse create(@PathVariable Long id, @Valid @RequestBody ConsultationMemoCreateRequest request, Authentication authentication) {
        return service.createMemo(id, request, authentication);
    }
}
