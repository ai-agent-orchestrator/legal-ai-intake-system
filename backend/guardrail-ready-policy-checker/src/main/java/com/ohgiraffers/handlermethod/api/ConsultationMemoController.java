package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.ConsultationMemoCreateRequest;
import com.ohgiraffers.handlermethod.dto.ConsultationMemoResponse;
import com.ohgiraffers.handlermethod.dto.IdempotentResult;
import com.ohgiraffers.handlermethod.service.LegalIntakeRecordService;
import com.ohgiraffers.handlermethod.support.RequestFingerprint;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/intakes")
public class ConsultationMemoController {
    private final LegalIntakeRecordService service;

    public ConsultationMemoController(LegalIntakeRecordService service) {
        this.service = service;
    }

    /* 화면07 상담 메모. 재전송이면 처음 저장한 메모를 201 + Idempotent-Replayed: true로 돌려준다. */
    @PostMapping("/{id}/memo")
    public ResponseEntity<ConsultationMemoResponse> create(@PathVariable Long id,
                                                           @Valid @RequestBody ConsultationMemoCreateRequest request,
                                                           @RequestHeader(value = RequestFingerprint.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
                                                           Authentication authentication) {
        IdempotentResult<ConsultationMemoResponse> result = service.createMemo(id, request, idempotencyKey, authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(RequestFingerprint.REPLAYED_HEADER, String.valueOf(result.replayed()))
                .body(result.body());
    }
}
