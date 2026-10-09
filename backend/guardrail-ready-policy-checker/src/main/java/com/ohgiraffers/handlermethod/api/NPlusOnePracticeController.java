package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.NPlusOnePracticeResponse;
import com.ohgiraffers.handlermethod.service.NPlusOnePracticeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/n-plus-one-practice")
public class NPlusOnePracticeController {

    private final NPlusOnePracticeService nPlusOnePracticeService;

    public NPlusOnePracticeController(NPlusOnePracticeService nPlusOnePracticeService) {
        this.nPlusOnePracticeService = nPlusOnePracticeService;
    }

    @PostMapping("/sample-data")
    public NPlusOnePracticeResponse createSampleData() {
        return nPlusOnePracticeService.createSampleData();
    }

    @GetMapping("/bad")
    public NPlusOnePracticeResponse badFindAll() {
        return nPlusOnePracticeService.badFindAll();
    }

    @GetMapping("/good")
    public NPlusOnePracticeResponse goodFindAll() {
        return nPlusOnePracticeService.goodFindAll();
    }
}
