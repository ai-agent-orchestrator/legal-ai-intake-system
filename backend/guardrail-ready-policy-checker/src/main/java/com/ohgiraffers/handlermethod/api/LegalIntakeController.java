package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.LegalIntakeAnalyzeRequest;
import com.ohgiraffers.handlermethod.dto.LegalIntakeAnalyzeResponse;
import com.ohgiraffers.handlermethod.dto.LegalIntakeHistoryResponse;
import com.ohgiraffers.handlermethod.dto.LegalIntakeSummaryResponse;
import com.ohgiraffers.handlermethod.service.LegalIntakeDecisionService;
import com.ohgiraffers.handlermethod.service.LegalIntakeQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/legal/intake")
public class LegalIntakeController {

    private final LegalIntakeDecisionService legalIntakeDecisionService;
    private final LegalIntakeQueryService legalIntakeQueryService;

    public LegalIntakeController(LegalIntakeDecisionService legalIntakeDecisionService,
                                 LegalIntakeQueryService legalIntakeQueryService) {
        this.legalIntakeDecisionService = legalIntakeDecisionService;
        this.legalIntakeQueryService = legalIntakeQueryService;
    }

    @PostMapping("/analyze")
    public LegalIntakeAnalyzeResponse analyze(@RequestBody LegalIntakeAnalyzeRequest request) {
        return legalIntakeDecisionService.analyze(request);
    }

    @GetMapping("/history")
    public List<LegalIntakeHistoryResponse> history(@RequestParam(required = false) String readinessLevel,
                                                    @RequestParam(required = false) String decision) {
        return legalIntakeQueryService.findRecent(readinessLevel, decision);
    }

    @GetMapping("/summary")
    public LegalIntakeSummaryResponse summary() {
        return legalIntakeQueryService.summarize();
    }
}
