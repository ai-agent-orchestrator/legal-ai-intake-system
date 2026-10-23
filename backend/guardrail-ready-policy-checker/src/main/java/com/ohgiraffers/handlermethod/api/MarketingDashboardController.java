package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.MarketingFunnelResponse;
import com.ohgiraffers.handlermethod.service.MarketingDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class MarketingDashboardController {
    private final MarketingDashboardService service;

    public MarketingDashboardController(MarketingDashboardService service) {
        this.service = service;
    }

    @GetMapping("/marketing-funnel")
    public MarketingFunnelResponse funnel() {
        return service.funnel();
    }
}
