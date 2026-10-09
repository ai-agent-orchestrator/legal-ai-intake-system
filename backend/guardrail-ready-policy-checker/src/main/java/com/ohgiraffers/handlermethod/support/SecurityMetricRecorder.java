package com.ohgiraffers.handlermethod.support;

import com.ohgiraffers.handlermethod.entity.SecurityEventHistory;
import com.ohgiraffers.handlermethod.repository.SecurityEventHistoryRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class SecurityMetricRecorder {

    private final MeterRegistry meterRegistry;
    private final SecurityEventHistoryRepository securityEventHistoryRepository;

    public SecurityMetricRecorder(MeterRegistry meterRegistry,
                                  SecurityEventHistoryRepository securityEventHistoryRepository) {
        this.meterRegistry = meterRegistry;
        this.securityEventHistoryRepository = securityEventHistoryRepository;
    }

    public void recordAuthFailure(HttpServletRequest request) {
        Counter.builder("security.auth.failures")
                .description("Total number of unauthenticated requests rejected by security")
                .tag("method", request.getMethod())
                .tag("uri", request.getRequestURI())
                .tag("status", "401")
                .register(meterRegistry)
                .increment();
        securityEventHistoryRepository.save(SecurityEventHistory.create(
                "AUTH_FAILURE",
                request.getMethod(),
                request.getRequestURI(),
                "401",
                "anonymous"
        ));
    }

    public void recordAccessDenied(HttpServletRequest request) {
        Counter.builder("security.access.denied")
                .description("Total number of authenticated requests denied by security")
                .tag("method", request.getMethod())
                .tag("uri", request.getRequestURI())
                .tag("status", "403")
                .register(meterRegistry)
                .increment();
        securityEventHistoryRepository.save(SecurityEventHistory.create(
                "ACCESS_DENIED",
                request.getMethod(),
                request.getRequestURI(),
                "403",
                request.getRemoteUser() == null ? "authenticated-user" : request.getRemoteUser()
        ));
    }
}
