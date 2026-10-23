package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.LegalIntakeClassificationRequest;
import com.ohgiraffers.handlermethod.dto.LegalIntakeClassificationResponse;
import com.ohgiraffers.handlermethod.service.LegalIntakeRecordService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/intakes")
public class LegalIntakeClassificationController {
    private final LegalIntakeRecordService service;

    public LegalIntakeClassificationController(LegalIntakeRecordService service) {
        this.service = service;
    }

    @PostMapping("/classify-preview")
    public LegalIntakeClassificationResponse preview(@Valid @RequestBody LegalIntakeClassificationRequest request) {
        return service.classifyPreview(request);
    }
}
