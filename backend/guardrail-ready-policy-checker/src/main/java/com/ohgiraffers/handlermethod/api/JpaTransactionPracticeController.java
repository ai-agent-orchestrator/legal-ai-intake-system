package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.PracticeMenuCreateRequest;
import com.ohgiraffers.handlermethod.dto.PracticeMenuResponse;
import com.ohgiraffers.handlermethod.dto.PracticeMenuUpdateRequest;
import com.ohgiraffers.handlermethod.service.JpaTransactionPracticeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transaction-practice/menus")
public class JpaTransactionPracticeController {

    private final JpaTransactionPracticeService jpaTransactionPracticeService;

    public JpaTransactionPracticeController(JpaTransactionPracticeService jpaTransactionPracticeService) {
        this.jpaTransactionPracticeService = jpaTransactionPracticeService;
    }

    @PostMapping
    public PracticeMenuResponse create(@Valid @RequestBody PracticeMenuCreateRequest request) {
        return jpaTransactionPracticeService.create(request);
    }

    @GetMapping("/{id}")
    public PracticeMenuResponse find(@PathVariable Long id) {
        return jpaTransactionPracticeService.find(id);
    }

    @PatchMapping("/{id}/good")
    public PracticeMenuResponse goodUpdate(@PathVariable Long id,
                                           @Valid @RequestBody PracticeMenuUpdateRequest request) {
        return jpaTransactionPracticeService.goodUpdate(id, request);
    }

    @PatchMapping("/{id}/bad")
    public PracticeMenuResponse badUpdate(@PathVariable Long id,
                                          @Valid @RequestBody PracticeMenuUpdateRequest request) {
        return jpaTransactionPracticeService.badUpdate(id, request);
    }
}
