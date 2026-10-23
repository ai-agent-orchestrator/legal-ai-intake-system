package com.ohgiraffers.handlermethod.legal;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/legal-intake")
public class MinimalLegalIntakeController {

    private final MinimalLegalIntakeService minimalLegalIntakeService;

    public MinimalLegalIntakeController(MinimalLegalIntakeService minimalLegalIntakeService) {
        this.minimalLegalIntakeService = minimalLegalIntakeService;
    }

    @PostMapping("/minimal")
    public ResponseEntity<MinimalLegalIntakeResponse> analyze(@Valid @RequestBody MinimalLegalIntakeRequest request) {
        return ResponseEntity.ok(minimalLegalIntakeService.analyze(request));
    }
}
