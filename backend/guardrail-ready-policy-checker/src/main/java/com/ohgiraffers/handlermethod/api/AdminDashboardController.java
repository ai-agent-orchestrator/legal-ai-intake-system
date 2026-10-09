package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.AdminDashboardSummaryResponse;
import com.ohgiraffers.handlermethod.dto.SecurityEventHistoryResponse;
import com.ohgiraffers.handlermethod.service.AdminDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping("/summary")
    public AdminDashboardSummaryResponse summary() {
        return adminDashboardService.summary();
    }

    @GetMapping("/security-events")
    public List<SecurityEventHistoryResponse> securityEvents() {
        return adminDashboardService.recentSecurityEvents();
    }
}
